-- V52: server-side record of platform-ops support (impersonation) sessions.
--
-- Until now a support session was only an oplog 'tenant.impersonate.start' row +
-- a 30-min token; termination was purely client-side (discard the token), so the
-- backend had no way to know a session ended early. The platform dashboard's
-- "active support sessions" metric therefore counted every start in the last
-- 30 min as live, even after the operator exited.
--
-- This table makes "live" knowable: one row per session, with started/expires and
-- a nullable ended_at. Active = ended_at IS NULL AND expires_at > now(). Written
-- by TenantImpersonationService (start = insert, terminate = set ended_at).
-- Insert-only audit-style row (no mark / soft-delete).

CREATE TABLE IF NOT EXISTS core_support_session (
    id           CHAR(36)     PRIMARY KEY,        -- the session_id (UUID) also carried in the JWT act claim
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'system',  -- owner (platform); the table is global ops data
    tenant_code  VARCHAR(64)  NOT NULL,           -- target tenant being supported
    operator     VARCHAR(64),                     -- ops username that started the session
    reason       VARCHAR(255),
    started_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP    NOT NULL,           -- started_at + token TTL (30 min)
    ended_at     TIMESTAMP                        -- set on explicit terminate; NULL = never terminated
);

COMMENT ON TABLE core_support_session IS
    'Platform-ops support (impersonation) sessions. Active = ended_at IS NULL AND expires_at > now().';

-- Cheap "who is live right now" lookup for the dashboard.
CREATE INDEX IF NOT EXISTS idx_core_support_session_active
    ON core_support_session (expires_at) WHERE ended_at IS NULL;

-- Recent-sessions list (newest first).
CREATE INDEX IF NOT EXISTS idx_core_support_session_started
    ON core_support_session (started_at DESC);
