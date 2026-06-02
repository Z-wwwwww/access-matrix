-- V34: core_notification — in-app notifications ("站内通知") with instant red dot.
--
-- One row per recipient (recipient_user_id). The DB row is the durable source
-- of truth for the unread badge; SSE only nudges the client to refresh it.
--
-- Scoping:
--   - tenant_id  : standard multi-tenant column. The MyBatis-Plus interceptor
--                  injects WHERE tenant_id=? ; TenantSchemaGuard requires it.
--   - per-user   : every query also filters recipient_user_id = current user
--                  (a personal inbox, NOT department data-scope).
--
-- No permission rows / menu rows are seeded: reading one's own notifications
-- needs only authentication (no @RequiresPermission), like /menu/me. The UI is
-- a header bell, not a left-menu entry.

CREATE TABLE IF NOT EXISTS core_notification (
    id                CHAR(26)     PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL DEFAULT 'default',
    recipient_user_id CHAR(26)     NOT NULL,
    type              VARCHAR(64)  NOT NULL,
    title             VARCHAR(256) NOT NULL,
    content           VARCHAR(2048),
    link              VARCHAR(512),
    biz_type          VARCHAR(64),
    biz_id            CHAR(26),
    level             SMALLINT     NOT NULL DEFAULT 1,   -- 1 info / 2 warn / 3 important
    read_flag         SMALLINT     NOT NULL DEFAULT 0,   -- 0 unread / 1 read
    read_time         TIMESTAMP,
    mark              SMALLINT     NOT NULL DEFAULT 1,
    create_user       VARCHAR(64),
    update_user       VARCHAR(64),
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The hot path: "unread count / inbox list for this user in this tenant".
CREATE INDEX IF NOT EXISTS idx_core_notification_inbox
    ON core_notification (tenant_id, recipient_user_id, read_flag, create_time DESC);
