package com.platform.core.bootstrap.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code app.migration.tenants} must not fall back to a tenant name.
 *
 * <p>It defaulted to {@code default} in two independent places — the
 * {@code application.yml} placeholder and {@link PasswordToSsoMigrationRunner}'s
 * {@code @Value} — and that tenant stopped existing when V25 renamed it to
 * {@code demo} ({@code core_tenant} holds demo / system / sozonext, no default).
 * The one-shot auth migration is gated on {@code run-on-startup}, so the damage
 * needed an operator to enable it without setting the tenant list: the runner
 * then split a non-empty CSV, skipped its own "nothing to do" guard, migrated a
 * tenant with no users, and wrote a success report showing zero — which reads
 * exactly like "everyone was already migrated".
 *
 * <p>Empty is the safe default: it reaches {@code tenants.isEmpty()} and logs
 * loudly. Pinned here because the default lives in two files that can drift apart.
 */
class MigrationTenantsDefaultTest {

    private static String read(String relativeToModule) throws IOException {
        return Files.readString(Path.of(relativeToModule), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("application.yml leaves the tenant list empty rather than guessing one")
    void yamlPlaceholderHasNoTenantFallback() throws IOException {
        String yml = read("src/main/resources/application.yml");

        assertThat(yml)
                .as("the placeholder must carry no default tenant")
                .contains("${CORE_MIGRATION_TENANTS:}");
        assertThat(yml)
                .as("`default` is not a tenant any more — V25 renamed it to demo")
                .doesNotContain("${CORE_MIGRATION_TENANTS:default}");
    }

    @Test
    @DisplayName("the runner's @Value default matches the yaml — the two can't drift")
    void runnerValueDefaultMatches() throws IOException {
        String runner = read(
                "src/main/java/com/platform/core/bootstrap/migration/PasswordToSsoMigrationRunner.java");

        assertThat(runner).contains("${app.migration.tenants:}");
        assertThat(runner)
                .as("a second, independent fallback is exactly how these drift apart")
                .doesNotContain("${app.migration.tenants:default}");
    }
}
