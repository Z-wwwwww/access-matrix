-- V22: invite-token table for the "admin invites user" provisioning flow.
--
-- Lifecycle:
--   1. UserAdminService.create(mode=INVITE):
--      - Keycloak user created (no credentials)
--      - row inserted here: token_hash = SHA-256(random 32 bytes)
--      - mail sent with the cleartext token in the URL
--   2. User clicks link → frontend /invite/<token> → POST /auth/invite/<token>
--      with the chosen password.
--   3. Backend hashes the supplied token, looks up the row, checks
--      expires_at > now AND used_at IS NULL, then:
--      - calls KeycloakUserService.setPassword(temporary=false)
--      - sets used_at = now (single-use)
--
-- Storage: only the SHA-256 of the token lives in the DB. Even a full DB
-- dump can't be used to log in — the attacker would need the cleartext
-- token from the original email.

CREATE TABLE IF NOT EXISTS core_user_invite (
    id           CHAR(26)     PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id      CHAR(26)     NOT NULL,
    keycloak_id  VARCHAR(64)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    expires_at   TIMESTAMP    NOT NULL,
    used_at      TIMESTAMP,
    mark         SMALLINT     NOT NULL DEFAULT 1,
    create_user  VARCHAR(64),
    update_user  VARCHAR(64),
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- token_hash is what the lookup keys on. Globally unique because the random
-- entropy (32 bytes) makes collisions astronomically unlikely; partial on
-- mark=1 so a soft-deleted invite doesn't burn the hash space.
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_user_invite_token_hash
    ON core_user_invite (token_hash)
 WHERE mark = 1;

-- For "list pending invites for user X" admin views.
CREATE INDEX IF NOT EXISTS idx_core_user_invite_user
    ON core_user_invite (tenant_id, user_id)
 WHERE mark = 1;

COMMENT ON TABLE  core_user_invite IS
    'Single-use invite tokens minted by UserAdminService.create(mode=INVITE). Hash-only storage.';
COMMENT ON COLUMN core_user_invite.token_hash IS
    'SHA-256 (hex) of the cleartext token that lives only in the recipient''s email.';
COMMENT ON COLUMN core_user_invite.used_at IS
    'Timestamp the token was consumed by the invite acceptance endpoint. Single-use enforcement.';
