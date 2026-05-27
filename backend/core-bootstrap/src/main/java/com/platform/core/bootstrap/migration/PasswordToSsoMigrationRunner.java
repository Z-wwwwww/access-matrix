package com.platform.core.bootstrap.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Auto-runs {@link PasswordToSsoMigrationService} once on backend startup
 * when the operator opts in via two config switches:
 *
 * <pre>
 * app:
 *   security:
 *     mode: oidc                           # required — migration only makes sense in oidc mode
 *   migration:
 *     run-on-startup: password-to-sso      # explicit opt-in (default off)
 *     tenants: default,acme,beta           # one realm/tenant id per item
 * </pre>
 *
 * Runs ONCE per backend start. After the migration completes:
 * <ul>
 *   <li>Summary line lands in the app log at INFO level.</li>
 *   <li>Full per-user breakdown lands at
 *       {@code logs/migration-password-to-sso-yyyyMMdd-HHmmss.json}
 *       (next to the app's other rotated logs).</li>
 *   <li>If everything succeeds, the operator should remove the
 *       {@code app.migration.run-on-startup} property — leaving it on
 *       doesn't cause harm (the service is idempotent — re-runs skip
 *       already-migrated users), but it does add a few seconds of admin
 *       API roundtrip to every restart.</li>
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
@ConditionalOnProperty(name = "app.migration.run-on-startup", havingValue = "password-to-sso")
public class PasswordToSsoMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordToSsoMigrationRunner.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final PasswordToSsoMigrationService service;
    private final JsonMapper json;

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
            log.warn("[migration] run-on-startup=password-to-sso but app.migration.tenants is empty — nothing to do");
            return;
        }
        log.info("[migration] starting password-to-sso for tenants={}", tenants);
        MigrationReport report = service.run(tenants);
        Path out = writeReport(report);
        log.info("[migration] complete: created={} skipped={} failed={} report={}",
                report.totalCreated(), report.totalSkipped(), report.totalFailed(), out);
        if (report.totalFailed() > 0) {
            // Surface the failure count loudly. We don't fail the boot — the
            // app should still come up so operators can debug + retry — but
            // we want a grep-able marker for monitoring.
            log.error("[migration] {} user(s) failed to migrate — see {}",
                    report.totalFailed(), out);
        }
    }

    private Path writeReport(MigrationReport report) {
        String filename = "migration-password-to-sso-" + LocalDateTime.now().format(TS) + ".json";
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
