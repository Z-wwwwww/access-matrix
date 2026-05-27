-- V24: reverse-migration support (SSO → password).
--
-- Two related changes shipped together because they're useless apart:
--
-- 1) core_auth_user.password_hash relaxed to NULLABLE.
--    V2 declared it NOT NULL — fine for password-only deployments. After
--    OIDC was added, OIDC-only users (JIT-provisioned) carry NULL here and
--    rely on MyBatis-Plus's NOT_NULL field strategy omitting the column
--    from INSERT statements. That works but is brittle. With the reverse
--    migration in play, the JIT bind path explicitly writes NULL to clear
--    a stale legacy hash — and that explicit UPDATE would violate the
--    constraint. Drop it.
--
-- 2) core_password_reset_token table — the reverse of core_user_invite.
--    Used by the SSO → password migration to email a "set your password"
--    link to every KC-bound user, so the operator can drop OIDC without
--    locking out the user base. Same hash-only storage as V22's invite
--    table; even a full DB dump can't be used to log in.
--
-- The data model rationale (why password_hash is allowed NULL):
--   In a fully-OIDC deployment, business users never know their KC
--   password (the IdP owns it); the column has no value in our DB. Only
--   SUPER_ADMIN rows keep a hash so admins can break-glass when KC is
--   unreachable. The constraint should reflect that reality.
--
-- ─────────────────────────────────────────────────────────────────────

ALTER TABLE core_auth_user
    ALTER COLUMN password_hash DROP NOT NULL;

COMMENT ON COLUMN core_auth_user.password_hash IS
    'NULLABLE — populated only for break-glass admins or legacy password-mode users. OIDC-managed users carry NULL here, with credentials owned by Keycloak.';

CREATE TABLE IF NOT EXISTS core_password_reset_token (
    id           CHAR(26)     PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id      CHAR(26)     NOT NULL,
    keycloak_id  VARCHAR(64),
    token_hash   VARCHAR(64)  NOT NULL,
    expires_at   TIMESTAMP    NOT NULL,
    used_at      TIMESTAMP,
    mark         SMALLINT     NOT NULL DEFAULT 1,
    create_user  VARCHAR(64),
    update_user  VARCHAR(64),
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- token_hash is what the lookup keys on. Partial on mark=1 so a soft-deleted
-- token doesn't burn the hash space.
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_password_reset_token_hash
    ON core_password_reset_token (token_hash)
 WHERE mark = 1;

-- For "outstanding reset tokens for user X" admin views.
CREATE INDEX IF NOT EXISTS idx_core_password_reset_token_user
    ON core_password_reset_token (tenant_id, user_id)
 WHERE mark = 1;

COMMENT ON TABLE  core_password_reset_token IS
    'Single-use reset tokens minted by the SSO → password reverse migration. Hash-only storage; cleartext lives only in the recipient''s email.';
COMMENT ON COLUMN core_password_reset_token.token_hash IS
    'SHA-256 (hex) of the cleartext token that lives only in the recipient''s email.';
COMMENT ON COLUMN core_password_reset_token.keycloak_id IS
    'Snapshot of the user''s Keycloak UUID at mint time (nullable: a user who never had a KC link can also have a reset token issued manually).';
COMMENT ON COLUMN core_password_reset_token.used_at IS
    'Timestamp the token was consumed by the reset-acceptance endpoint. Single-use enforcement.';
