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

            // ---------- V5 RBAC tables (idempotent safety net) ----------
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_rbac_role (
                        id CHAR(26) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        code VARCHAR(64) NOT NULL,
                        name VARCHAR(128) NOT NULL,
                        description VARCHAR(512),
                        data_scope SMALLINT NOT NULL DEFAULT 4,
                        is_built_in SMALLINT NOT NULL DEFAULT 0,
                        status SMALLINT NOT NULL DEFAULT 1,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        mark SMALLINT NOT NULL DEFAULT 1,
                        create_user VARCHAR(64),
                        update_user VARCHAR(64),
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_role_code ON core_rbac_role (tenant_id, code) WHERE mark = 1");

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_rbac_permission (
                        id CHAR(26) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        code VARCHAR(128) NOT NULL,
                        name VARCHAR(128) NOT NULL,
                        resource VARCHAR(64) NOT NULL,
                        action VARCHAR(64) NOT NULL,
                        module VARCHAR(32),
                        description VARCHAR(512),
                        is_built_in SMALLINT NOT NULL DEFAULT 0,
                        mark SMALLINT NOT NULL DEFAULT 1,
                        create_user VARCHAR(64),
                        update_user VARCHAR(64),
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_perm_code ON core_rbac_permission (tenant_id, code) WHERE mark = 1");

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_rbac_user_role (
                        id CHAR(26) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        user_id CHAR(26) NOT NULL,
                        role_id CHAR(26) NOT NULL,
                        mark SMALLINT NOT NULL DEFAULT 1,
                        create_user VARCHAR(64),
                        update_user VARCHAR(64),
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_user_role ON core_rbac_user_role (tenant_id, user_id, role_id) WHERE mark = 1");

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_rbac_role_permission (
                        id CHAR(26) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        role_id CHAR(26) NOT NULL,
                        permission_id CHAR(26) NOT NULL,
                        mark SMALLINT NOT NULL DEFAULT 1,
                        create_user VARCHAR(64),
                        update_user VARCHAR(64),
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_role_perm ON core_rbac_role_permission (tenant_id, role_id, permission_id) WHERE mark = 1");

            // Built-in seeds (idempotent ON CONFLICT)
            jdbc.update("""
                    INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in)
                    VALUES ('00000000000000000000PERM01', 'default', '*:*', 'Super Permission', '*', '*', 'system', 1)
                    ON CONFLICT DO NOTHING
                    """);
            jdbc.update("""
                    INSERT INTO core_rbac_role (id, tenant_id, code, name, description, data_scope, is_built_in)
                    VALUES ('00000000000000000000ROLE01', 'default', 'SUPER_ADMIN', 'Super Administrator',
                            'Built-in super admin role with *:* permission', 1, 1)
                    ON CONFLICT DO NOTHING
                    """);
            jdbc.update("""
                    INSERT INTO core_rbac_role_permission (id, tenant_id, role_id, permission_id)
                    VALUES ('00000000000000000000RPM001', 'default',
                            '00000000000000000000ROLE01', '00000000000000000000PERM01')
                    ON CONFLICT DO NOTHING
                    """);

            // ---------- V6 menu tables ----------
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_rbac_menu (
                        id CHAR(26) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        parent_id CHAR(26),
                        code VARCHAR(64) NOT NULL,
                        title VARCHAR(128) NOT NULL,
                        menu_type SMALLINT NOT NULL,
                        path VARCHAR(255),
                        component VARCHAR(255),
                        icon VARCHAR(64),
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        hide SMALLINT NOT NULL DEFAULT 0,
                        hide_footer SMALLINT NOT NULL DEFAULT 0,
                        hide_sidebar SMALLINT NOT NULL DEFAULT 0,
                        tab_unique VARCHAR(64),
                        redirect VARCHAR(255),
                        permission_code VARCHAR(128),
                        status SMALLINT NOT NULL DEFAULT 1,
                        mark SMALLINT NOT NULL DEFAULT 1,
                        create_user VARCHAR(64),
                        update_user VARCHAR(64),
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_menu_code ON core_rbac_menu (tenant_id, code) WHERE mark = 1");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_core_rbac_menu_parent ON core_rbac_menu (tenant_id, parent_id, sort_order) WHERE mark = 1");

            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS core_rbac_role_menu (
                        id CHAR(26) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                        role_id CHAR(26) NOT NULL,
                        menu_id CHAR(26) NOT NULL,
                        mark SMALLINT NOT NULL DEFAULT 1,
                        create_user VARCHAR(64),
                        update_user VARCHAR(64),
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_role_menu ON core_rbac_role_menu (tenant_id, role_id, menu_id) WHERE mark = 1");

            log.info("AuthSchemaBootstrap ensured schema integrity (idempotent).");
        } catch (Exception e) {
            log.error("AuthSchemaBootstrap failed", e);
            throw e;
        }
    }
}
