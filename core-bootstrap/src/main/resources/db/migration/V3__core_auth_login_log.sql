-- V3: core_auth_login_log (async write)
CREATE TABLE IF NOT EXISTS core_auth_login_log (
    id              CHAR(26)     PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id         CHAR(26),
    identifier      VARCHAR(128),
    client_ip       VARCHAR(64),
    user_agent      VARCHAR(512),
    success         BOOLEAN      NOT NULL,
    failure_reason  VARCHAR(128),
    login_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_core_auth_login_log_user_id
    ON core_auth_login_log (user_id);
CREATE INDEX IF NOT EXISTS idx_core_auth_login_log_login_time
    ON core_auth_login_log (login_time);
CREATE INDEX IF NOT EXISTS idx_core_auth_login_log_tenant
    ON core_auth_login_log (tenant_id, login_time);

COMMENT ON TABLE core_auth_login_log IS 'Login audit log (every attempt, success or failure)';
