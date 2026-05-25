-- V18: drop core_rbac_role.code
-- Internal references switch to the seeded ULID (e.g. SUPER_ADMIN's id =
-- '00000000000000000000ROLE01'). `name` becomes the sole user-facing label
-- and gains a tenant-scoped uniqueness constraint to keep admin disambiguation.

DROP INDEX IF EXISTS uk_core_rbac_role_code;

ALTER TABLE core_rbac_role
    DROP COLUMN IF EXISTS code;

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_role_name
    ON core_rbac_role (tenant_id, name) WHERE mark = 1;
