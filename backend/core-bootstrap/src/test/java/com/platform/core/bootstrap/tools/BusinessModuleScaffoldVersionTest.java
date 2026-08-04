package com.platform.core.bootstrap.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scaffold's Flyway version picker must see EVERY migration directory, because
 * they share one version space.
 *
 * <p>{@code spring.flyway.locations} is {@code classpath:db/migration}, which Flyway
 * resolves across every jar and classes directory on the runtime classpath. So a
 * migration in {@code business-orders/src/main/resources/db/migration} and one in
 * {@code core-bootstrap/src/main/resources/db/migration} are peers — two files with
 * the same V-number are duplicates and Flyway refuses to start ("Found more than one
 * migration with version N").
 *
 * <p>The bug: new-module mode WRITES into the module's own directory but READ the next
 * version from core-bootstrap's only. So the first scaffolded module got V1000, and
 * every module after it got V1000 again — the second one broke startup. Legacy mode
 * then also collided, since it too picked from a directory that never saw the module
 * migrations.
 */
class BusinessModuleScaffoldVersionTest {

    private static void mig(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), "-- test\n");
    }

    private static Path bootstrapMigrations(Path repo) {
        return repo.resolve("backend/core-bootstrap/src/main/resources/db/migration");
    }

    private static Path moduleMigrations(Path repo, String artifactId) {
        return repo.resolve("backend").resolve(artifactId).resolve("src/main/resources/db/migration");
    }

    @Test
    void frameworkOnly_startsTheBusinessSpaceAt1000(@TempDir Path repo) throws IOException {
        mig(bootstrapMigrations(repo), "V58__timestamp_to_timestamptz.sql");

        assertThat(BusinessModuleScaffold.pickNextFlywayVersion(repo)).isEqualTo(1000);
    }

    @Test
    void aModuleMigrationIsCounted_soTheNextScaffoldDoesNotReuseItsVersion(@TempDir Path repo)
            throws IOException {
        mig(bootstrapMigrations(repo), "V58__timestamp_to_timestamptz.sql");
        // What the first scaffolded module left behind.
        mig(moduleMigrations(repo, "business-orders"), "V1000__create_business_order.sql");

        assertThat(BusinessModuleScaffold.pickNextFlywayVersion(repo))
                .as("scanning core-bootstrap alone would answer 1000 again and collide")
                .isEqualTo(1001);
    }

    @Test
    void theHighestAcrossAllDirectoriesWins(@TempDir Path repo) throws IOException {
        mig(bootstrapMigrations(repo), "V58__x.sql");
        mig(bootstrapMigrations(repo), "V1000__x.sql");
        mig(moduleMigrations(repo, "business-orders"), "V1001__x.sql");
        mig(moduleMigrations(repo, "business-billing"), "V1007__x.sql");

        assertThat(BusinessModuleScaffold.pickNextFlywayVersion(repo)).isEqualTo(1008);
    }

    @Test
    void allMigrationDirsFindsBootstrapPlusEveryModule(@TempDir Path repo) throws IOException {
        mig(bootstrapMigrations(repo), "V1__x.sql");
        mig(moduleMigrations(repo, "business-orders"), "V1000__x.sql");
        // A module without migrations must not appear, and neither must a stray dir.
        Files.createDirectories(repo.resolve("backend/business-demo/src/main/java"));

        List<Path> dirs = BusinessModuleScaffold.allMigrationDirs(repo);

        assertThat(dirs).containsExactly(
                bootstrapMigrations(repo),
                moduleMigrations(repo, "business-orders"));
    }

    @Test
    void missingDirectoriesAreTolerated(@TempDir Path repo) throws IOException {
        // Fresh checkout / unexpected layout must not blow up the tool.
        assertThat(BusinessModuleScaffold.pickNextFlywayVersion(repo)).isEqualTo(1000);
        assertThat(BusinessModuleScaffold.maxVersionIn(repo.resolve("nope"))).isZero();
    }

    @Test
    void nonMigrationFilesAreIgnored(@TempDir Path repo) throws IOException {
        Path d = bootstrapMigrations(repo);
        mig(d, "V1000__x.sql");
        Files.writeString(d.resolve("README.md"), "notes");
        Files.writeString(d.resolve("V_bad__x.sql"), "-- unparsable version");

        assertThat(BusinessModuleScaffold.maxVersionIn(d)).isEqualTo(1000);
    }
}
