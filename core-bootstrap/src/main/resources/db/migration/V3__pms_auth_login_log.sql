-- V3: pms_auth_login_log (async write)
CREATE TABLE IF NOT EXISTS pms_auth_login_log (
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

CREATE INDEX IF NOT EXISTS idx_pms_auth_login_log_user_id
    ON pms_auth_login_log (user_id);
CREATE INDEX IF NOT EXISTS idx_pms_auth_login_log_login_time
    ON pms_auth_login_log (login_time);
CREATE INDEX IF NOT EXISTS idx_pms_auth_login_log_tenant
    ON pms_auth_login_log (tenant_id, login_time);

COMMENT ON TABLE pms_auth_login_log IS 'Login audit log (every attempt, success or failure)';
