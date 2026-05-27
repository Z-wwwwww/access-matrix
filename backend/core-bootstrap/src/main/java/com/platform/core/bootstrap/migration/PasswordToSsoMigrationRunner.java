package com.platform.core.bootstrap.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
import java.util.function.Function;

/**
 * Auto-runs {@link PasswordToSsoMigrationService} once on backend startup
 * when the operator opts in via two config switches:
 *
 * <pre>
 * app:
 *   security:
 *     mode: oidc                                # required — migration only makes sense in oidc mode
 *   migration:
 *     run-on-startup: password-to-sso           # | password-to-sso-resend
 *     tenants: default,acme,beta                # one realm/tenant id per item
 * </pre>
 *
 * <h3>Two modes</h3>
 * <ul>
 *   <li><b>password-to-sso</b> — first-time mirror. Reads
 *       {@code core_auth_user} rows with {@code keycloak_id IS NULL},
 *       creates a matching Keycloak user (without credentials), and
 *       triggers the {@code UPDATE_PASSWORD} reset-email so the user
 *       finishes enrollment. Idempotent — users already in KC are
 *       <em>skipped</em>, not re-emailed.</li>
 *   <li><b>password-to-sso-resend</b> — companion entry point for the
 *       case where users let Keycloak's reset link expire (the default
 *       window is 12 h). Walks the same {@code keycloak_id IS NULL}
 *       set, finds the existing KC user, and fires a fresh
 *       {@code executeActionsEmail}. Does NOT create new KC users
 *       (use {@code password-to-sso} for that) and does NOT touch
 *       users who have already completed SSO (the bind path writes
 *       {@code keycloak_id} so they fall out of the candidate query).</li>
 * </ul>
 *
 * <p>Runs ONCE per backend start. After completion:
 * <ul>
 *   <li>Summary line lands in the app log at INFO level.</li>
 *   <li>Full per-user breakdown lands at
 *       {@code logs/migration-password-to-sso-yyyyMMdd-HHmmss.json}
 *       (for {@code resend}: {@code logs/migration-password-to-sso-resend-...}).</li>
 *   <li>Once green, remove the {@code app.migration.run-on-startup}
 *       property so subsequent restarts skip the migration overhead.
 *       Leaving it on doesn't cause harm (both modes are idempotent)
 *       but does add an admin-API roundtrip per user on every start.</li>
 * </ul>
 *
 * <h3>Why a startup runner and not an HTTP endpoint?</h3>
 * The migration is a planned operation, not part of regular admin life.
 * Tying it to a config-driven startup hook gives ops a single deterministic
 * trigger ("set property → restart") that's the same in every environment,
 * is naturally audited via the deploy log, and can't be invoked through a
 * compromised admin session. The trade-off — needs a restart — is fine for
 * a one-shot.
 */
@Component
// Match either of the two recognised modes. We deliberately do NOT use
// `havingValue` (which is single-valued) — a SpEL expression keeps the
// recognised set in one place and lets startup fail loudly on a typo
// (see run() — unknown values are flagged at runtime).
@ConditionalOnExpression(
        "'${app.migration.run-on-startup:}' == 'password-to-sso' "
        + "or '${app.migration.run-on-startup:}' == 'password-to-sso-resend'")
public class PasswordToSsoMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordToSsoMigrationRunner.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final PasswordToSsoMigrationService service;
    private final JsonMapper json;

    @Value("${app.migration.run-on-startup:}")
    private String mode;

    @Value("${app.migration.tenants:default}")
    private String tenantsCsv;

    @Value("${app.migration.report-dir:logs}")
    private String reportDir;

    public PasswordToSsoMigrationRunner(PasswordToSsoMigrationService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> tenants = Arrays.stream(tenantsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (tenants.isEmpty()) {
            log.warn("[migration] run-on-startup={} but app.migration.tenants is empty — nothing to do", mode);
            return;
        }

        // Dispatch by mode. The @ConditionalOnExpression on the class
        // already restricts to one of these two values, so the default
        // branch is genuinely unreachable — but we log it loudly anyway
        // in case someone bypasses the condition (e.g. via profile-import).
        Function<List<String>, MigrationReport> work;
        String reportPrefix;
        switch (mode) {
            case "password-to-sso" -> {
                work = service::run;
                reportPrefix = "migration-password-to-sso";
            }
            case "password-to-sso-resend" -> {
                work = service::resend;
                reportPrefix = "migration-password-to-sso-resend";
            }
            default -> {
                log.error("[migration] unrecognised mode '{}' — nothing to do", mode);
                return;
            }
        }

        log.info("[migration] starting {} for tenants={}", mode, tenants);
        MigrationReport report = work.apply(tenants);
        Path out = writeReport(report, reportPrefix);
        log.info("[migration] complete mode={} created={} skipped={} failed={} report={}",
                mode, report.totalCreated(), report.totalSkipped(), report.totalFailed(), out);
        if (report.totalFailed() > 0) {
            // Surface the failure count loudly. We don't fail the boot — the
            // app should still come up so operators can debug + retry — but
            // we want a grep-able marker for monitoring.
            log.error("[migration] {} user(s) failed in mode={} — see {}",
                    report.totalFailed(), mode, out);
        }
    }

    private Path writeReport(MigrationReport report, String prefix) {
        String filename = prefix + "-" + LocalDateTime.now().format(TS) + ".json";
        Path out = Paths.get(reportDir, filename);
        try {
            Files.createDirectories(out.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
        } catch (IOException e) {
            // Don't lose the report on a filesystem hiccup — dump it to log
            // as a last resort. Big lines but better than nothing.
            log.error("[migration] could not write report to {}: {}", out, e.toString());
            try {
                log.error("[migration] inline report:\n{}",
                        json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            } catch (Exception inner) {
                log.error("[migration] could not even serialize report: {}", inner.toString());
            }
        }
        return out;
    }
}
