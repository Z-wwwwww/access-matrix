package com.platform.core.bootstrap.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Force {@code flyway.repair()} to run before every {@code migrate()} so any
 * checksum drift on already-applied migrations is silently absorbed.
 *
 * <p>Why this exists: {@code application.yml} sets
 * {@code spring.flyway.repair-on-migrate=true}, but on Spring Boot 4 /
 * Flyway 11 that property is no longer honored automatically. Registering an
 * explicit {@link FlywayMigrationStrategy} bean overrides the auto-configured
 * "migrate-only" strategy and produces the behavior the property describes.
 *
 * <p>Safe to run unconditionally: {@code repair()} only rewrites the local
 * {@code flyway_schema_history} table to match the on-disk script checksums.
 * It does not re-execute migrations and is a no-op when checksums already
 * agree. Every migration script in this repo is written to be idempotent
 * (CREATE IF NOT EXISTS / DO $$ ... pg_constraint guards), so even an
 * accidental re-run is harmless.
 */
@Configuration
public class FlywayRepairConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("Flyway: running repair() before migrate() to absorb any checksum drift");
            flyway.repair();
            flyway.migrate();
        };
    }
}
