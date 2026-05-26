-- V21: link core_auth_user to its Keycloak identity.
--
-- Why: we keep our own ULID (id) as the business primary key — every
-- foreign reference (user_role, oplog, deptassignment, …) already cites
-- it, plus historical data we don't want to migrate. The Keycloak user
-- UUID lives in this new column and is the external "identity anchor"
-- the JIT provisioning step looks up when an OIDC token first arrives.
--
-- Index is partial on (mark = 1 AND keycloak_id IS NOT NULL) so:
--   * soft-deleted rows don't burn a Keycloak UUID for any future
--     re-provisioning of the same identity;
--   * users still managed by the legacy password flow (no Keycloak
--     binding yet) don't trip the unique constraint by all having NULL.
-- Scoped to (tenant_id, keycloak_id) — Keycloak UUIDs are globally
-- unique, but the index sits inside the multi-tenant uniqueness pattern
-- the rest of the table already follows (see V20).

ALTER TABLE core_auth_user
    ADD COLUMN IF NOT EXISTS keycloak_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_tenant_keycloak_id
    ON core_auth_user (tenant_id, keycloak_id)
 WHERE mark = 1 AND keycloak_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_core_auth_user_keycloak_id
    ON core_auth_user (keycloak_id)
 WHERE keycloak_id IS NOT NULL;

COMMENT ON COLUMN core_auth_user.keycloak_id IS
    'Keycloak user UUID. NULL = user is still on the legacy password path or has not yet logged in via OIDC. Filled by OidcJitUserService on the first JWT seen for this identity.';
