-- V2: core_auth_user
CREATE TABLE IF NOT EXISTS core_auth_user (
    id            CHAR(26)      PRIMARY KEY,
    tenant_id     VARCHAR(64)   NOT NULL DEFAULT 'default',
    username      VARCHAR(64)   NOT NULL,
    email         VARCHAR(255),
    user_no       VARCHAR(32),
    display_name  VARCHAR(128),
    password_hash VARCHAR(255)  NOT NULL,
    roles         JSONB         NOT NULL DEFAULT '[]'::jsonb,
    authorities   JSONB         NOT NULL DEFAULT '[]'::jsonb,
    status        SMALLINT      NOT NULL DEFAULT 1,
    mark          SMALLINT      NOT NULL DEFAULT 1,
    create_user   VARCHAR(64),
    update_user   VARCHAR(64),
    create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_username
    ON core_auth_user (username) WHERE mark = 1;
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_email
    ON core_auth_user (email) WHERE mark = 1 AND email IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_user_no
    ON core_auth_user (user_no) WHERE mark = 1 AND user_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_core_auth_user_tenant
    ON core_auth_user (tenant_id, status);

COMMENT ON TABLE core_auth_user IS 'Platform authentication user';
COMMENT ON COLUMN core_auth_user.status IS '1=enabled, 0=locked';
COMMENT ON COLUMN core_auth_user.mark IS '1=active, 0=deleted (logic delete)';
