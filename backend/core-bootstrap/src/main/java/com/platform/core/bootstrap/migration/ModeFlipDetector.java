package com.platform.core.bootstrap.migration;

import com.platform.core.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Opt-in companion to {@link PasswordToSsoMigrationRunner}: detects a
 * change in {@code app.security.mode} since the last successful boot and
 * automatically dispatches the matching migration. The "just flip mode"
 * experience that means the operator does not need to remember a second
 * {@code app.migration.run-on-startup} flag.
 *
 * <h3>Activation</h3>
 * <pre>
 * app:
 *   migration:
 *     auto-on-mode-flip: true          # explicit opt-in (default: false)
 *     tenants: default,acme,beta       # same list the explicit runner uses
 * </pre>
 * Off by default. The risk model for "on by default" is that a developer
 * flipping mode locally would accidentally trigger a multi-thousand-user
 * email blast against production; keeping it explicit means the operator
 * has to actively opt in to that behavior on each environment.
 *
 * <h3>State</h3>
 * Last-applied mode lives in {@code core_meta.meta_value} under key
 * {@code security.last_applied_mode}. The detector compares against the
 * current {@code app.security.mode} value:
 *
 * <pre>
 *   last        current        → action
 *   ──────────────────────────────────────────────────────────────────
 *   (absent)    *              → no migration; first boot baseline only
 *   password    oidc           → run PasswordToSsoMigrationService.run
 *   oidc        password       → run SsoToPasswordMigrationService.run
 *   permit-all  oidc/password  → no migration; permit-all is dev-only,
 *                                 treat the first non-dev mode as baseline
 *   *           same           → no-op
 *   *           permit-all     → no-op (don't flap on flipping to dev mode)
 * </pre>
 *
 * Whatever happens, the current mode is persisted to {@code core_meta} on
 * exit so the next boot picks up from the new baseline.
 *
 * <h3>Order</h3>
 * Runs at {@code Ordered.LOWEST_PRECEDENCE} — after every other
 * {@code ApplicationRunner} so:
 * <ul>
 *   <li>Flyway / schema setup is done.</li>
 *   <li>Seeders (LocalAdminSeeder, LocalKeycloakAdminSeeder) have run.</li>
 *   <li>If the operator ALSO set an explicit {@code run-on-startup}, that
 *       runner has already finished and the detector sees a state where
 *       the migration is essentially complete (subsequent users land in
 *       the skipped bucket).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.migration.auto-on-mode-flip", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE)
public class ModeFlipDetector implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModeFlipDetector.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Meta-table row key storing the last-applied security mode. */
    private static final String META_KEY = "security.last_applied_mode";

    private final JdbcTemplate jdbc;
    private final ObjectProvider<PasswordToSsoMigrationService> forwardProvider;
    private final ObjectProvider<SsoToPasswordMigrationService> reverseProvider;
    private final JsonMapper json;

    @Value("${app.security.mode:permit-all}")
    private String currentMode;

    @Value("${app.migration.tenants:default}")
    private String tenantsCsv;

    @Value("${app.migration.report-dir:logs}")
    private String reportDir;

    public ModeFlipDetector(JdbcTemplate jdbc,
                            ObjectProvider<PasswordToSsoMigrationService> forwardProvider,
                            ObjectProvider<SsoToPasswordMigrationService> reverseProvider,
                            JsonMapper json) {
        this.jdbc = jdbc;
        this.forwardProvider = forwardProvider;
        this.reverseProvider = reverseProvider;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        String last = readLastApplied();
        String current = currentMode == null ? "permit-all" : currentMode.toLowerCase();
        log.info("[mode-flip] auto-detect: last={} current={}", last, current);

        try {
            // No baseline yet → just write current and exit. Don't trigger a
            // migration on the very first boot; that's the operator's job to
            // do explicitly the first time so they can review the report.
            if (last == null || last.isBlank()) {
                log.info("[mode-flip] no baseline yet — recording current mode as the baseline");
                return;
            }
            if (last.equals(current)) {
                return;   // common case, no transition
            }
            // permit-all is dev-only; never treat transitions involving it as
            // a real migration. This keeps "flip to permit-all for a smoke test"
            // from blowing up production data on the way back.
            if ("permit-all".equals(last) || "permit-all".equals(current)) {
                log.info("[mode-flip] permit-all involved — skipping migration, just rebaselining");
                return;
            }

            List<String> tenants = parseTenants();
            if (tenants.isEmpty()) {
                log.warn("[mode-flip] tenants list is empty — nothing to migrate");
                return;
            }

            if ("password".equals(last) && "oidc".equals(current)) {
                runForward(tenants);
            } else if ("oidc".equals(last) && "password".equals(current)) {
                runReverse(tenants);
            } else {
                log.info("[mode-flip] unhandled transition {} → {} — skipping", last, current);
            }
        } finally {
            // Always rebaseline on exit so a partial / failed migration
            // doesn't trigger AGAIN on the next restart. Operator should
            // read the JSON report and re-run explicitly if needed.
            writeLastApplied(current);
        }
    }

    private void runForward(List<String> tenants) {
        PasswordToSsoMigrationService svc = forwardProvider.getIfAvailable();
        if (svc == null) {
            log.error("[mode-flip] password→oidc detected but forward service unavailable");
            return;
        }
        log.info("[mode-flip] detected password → oidc, running forward migration on tenants={}", tenants);
        MigrationReport report = svc.run(tenants);
        writeReport(report, "migration-mode-flip-forward");
        log.info("[mode-flip] forward complete: created={} skipped={} failed={}",
                report.totalCreated(), report.totalSkipped(), report.totalFailed());
    }

    private void runReverse(List<String> tenants) {
        SsoToPasswordMigrationService svc = reverseProvider.getIfAvailable();
        if (svc == null) {
            log.error("[mode-flip] oidc→password detected but reverse service unavailable");
            return;
        }
        log.info("[mode-flip] detected oidc → password, running reverse migration on tenants={}", tenants);
        MigrationReport report = svc.run(tenants);
        writeReport(report, "migration-mode-flip-reverse");
        log.info("[mode-flip] reverse complete: created={} skipped={} failed={}",
                report.totalCreated(), report.totalSkipped(), report.totalFailed());
    }

    private List<String> parseTenants() {
        return Arrays.stream(tenantsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ─── core_meta IO ─────────────────────────────────────────────────

    private String readLastApplied() {
        try {
            return jdbc.queryForObject(
                    "SELECT meta_value FROM core_meta WHERE meta_key = ?",
                    String.class, META_KEY);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            log.warn("[mode-flip] could not read last-applied mode ({}), treating as absent", e.toString());
            return null;
        }
    }

    private void writeLastApplied(String mode) {
        try {
            int updated = jdbc.update(
                    "UPDATE core_meta SET meta_value = ? WHERE meta_key = ?",
                    mode, META_KEY);
            if (updated == 0) {
                jdbc.update("INSERT INTO core_meta (id, meta_key, meta_value) VALUES (?, ?, ?) "
                                + "ON CONFLICT (meta_key) DO UPDATE SET meta_value = EXCLUDED.meta_value",
                        IdGenerator.ulid(), META_KEY, mode);
            }
        } catch (Exception e) {
            // Persisting baseline is best-effort. Failing here re-fires the
            // SAME migration on the next boot — annoying but idempotent.
            log.error("[mode-flip] could not persist baseline mode ({}): {}", mode, e.toString());
        }
    }

    private void writeReport(MigrationReport report, String prefix) {
        String filename = prefix + "-" + LocalDateTime.now().format(TS) + ".json";
        Path out = Paths.get(reportDir, filename);
        try {
            Files.createDirectories(out.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
            log.info("[mode-flip] report written to {}", out);
        } catch (IOException e) {
            log.error("[mode-flip] could not write report {}: {}", out, e.toString());
        }
    }
}
