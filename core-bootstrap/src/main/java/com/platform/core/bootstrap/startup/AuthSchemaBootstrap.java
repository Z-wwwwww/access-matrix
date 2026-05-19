package com.platform.core.bootstrap.startup;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PG-native idempotent DDL safety net.
 * Flyway runs first; this only adds missing tables/columns on a dirty schema_history.
 */
@Component
public class AuthSchemaBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AuthSchemaBootstrap.class);

    private final JdbcTemplate jdbc;

    public AuthSchemaBootstrap(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void ensureSchema() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_meta (
                        id CHAR(26) PRIMARY KEY,
                        meta_key VARCHAR(64) UNIQUE NOT NULL,
                        meta_value TEXT,
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_auth_user (
                        id CHAR(26) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        username VARCHAR(64) NOT NULL,
                        email VARCHAR(255),
                        user_no VARCHAR(32),
                        display_name VARCHAR(128),
                        password_hash VARCHAR(255) NOT NULL,
                        roles JSONB NOT NULL DEFAULT '[]'::jsonb,
                        authorities JSONB NOT NULL DEFAULT '[]'::jsonb,
                        status SMALLINT NOT NULL DEFAULT 1,
                        mark SMALLINT NOT NULL DEFAULT 1,
                        create_user VARCHAR(64),
                        update_user VARCHAR(64),
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            jdbc.execute("ALTER TABLE core_auth_user ADD COLUMN IF NOT EXISTS user_no VARCHAR(32)");
            jdbc.execute("ALTER TABLE core_auth_user ADD COLUMN IF NOT EXISTS email VARCHAR(255)");
            jdbc.execute("ALTER TABLE core_auth_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(128)");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_username ON core_auth_user (username) WHERE mark = 1");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_email ON core_auth_user (email) WHERE mark = 1 AND email IS NOT NULL");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_user_no ON core_auth_user (user_no) WHERE mark = 1 AND user_no IS NOT NULL");

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_auth_login_log (
                        id CHAR(26) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        user_id CHAR(26),
                        identifier VARCHAR(128),
                        client_ip VARCHAR(64),
                        user_agent VARCHAR(512),
                        success BOOLEAN NOT NULL,
                        failure_reason VARCHAR(128),
                        login_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_core_auth_login_log_user_id ON core_auth_login_log (user_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_core_auth_login_log_login_time ON core_auth_login_log (login_time)");

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_numbering_management (
                        code_kbn VARCHAR(64) PRIMARY KEY,
                        format_sentence VARCHAR(255) NOT NULL,
                        recycle_division SMALLINT NOT NULL DEFAULT 0,
                        zero_insert VARCHAR(8) DEFAULT '0',
                        seq_id_digit SMALLINT NOT NULL DEFAULT 6,
                        date_format_sentence VARCHAR(32),
                        min_value BIGINT NOT NULL DEFAULT 1,
                        max_value BIGINT NOT NULL DEFAULT 999999,
                        step_value BIGINT NOT NULL DEFAULT 1,
                        seq_id BIGINT NOT NULL DEFAULT 0,
                        description VARCHAR(255)
                    )
                    """);

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_numbering_key (
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        code_kbn VARCHAR(64) NOT NULL,
                        numbering_key VARCHAR(255) NOT NULL,
                        seq_id BIGINT NOT NULL DEFAULT 0,
                        CONSTRAINT pk_core_numbering_key PRIMARY KEY (tenant_id, code_kbn, numbering_key)
                    )
                    """);
            log.info("AuthSchemaBootstrap ensured schema integrity (idempotent).");
        } catch (Exception e) {
            log.error("AuthSchemaBootstrap failed", e);
            throw e;
        }
    }
}
