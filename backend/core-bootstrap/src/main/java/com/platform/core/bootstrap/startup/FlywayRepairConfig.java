package com.platform.core.bootstrap.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom {@link FlywayMigrationStrategy} that optionally runs {@code repair()}
 * before {@code migrate()}, driven by {@code spring.flyway.repair-on-migrate}.
 *
 * <p>Why this exists: on Spring Boot 4 / Flyway 11 the
 * {@code spring.flyway.repair-on-migrate} property is no longer honored by the
 * auto-configured "migrate-only" strategy. Registering this bean re-implements
 * it so the property means what it says again.
 *
 * <p>Per-environment intent:
 * <ul>
 *   <li>dev / test (base default {@code true}): repair() first, silently
 *       absorbing checksum drift on already-applied migrations — convenient
 *       while iterating locally.</li>
 *   <li>prod ({@code false}): NO repair() — a checksum mismatch fails the
 *       migration loudly, surfacing a tampered/diverged history instead of
 *       quietly rewriting {@code flyway_schema_history} to match.</li>
 * </ul>
 *
 * <p>{@code repair()} only rewrites the local {@code flyway_schema_history}
 * table to match on-disk checksums; it never re-executes migrations and is a
 * no-op when checksums already agree.
 */
@Configuration
public class FlywayRepairConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${spring.flyway.repair-on-migrate:true}") boolean repairOnMigrate) {
        return flyway -> {
            if (repairOnMigrate) {
                log.info("Flyway: repair() before migrate() to absorb any checksum drift");
                flyway.repair();
            } else {
                log.info("Flyway: repair-on-migrate=false — skipping repair(); a checksum mismatch will fail the migration");
            }
            flyway.migrate();
        };
    }
}
