-- V26: seed PLATFORM_ADMIN role + platform:* permission under the 'system' tenant.
--
-- WHY
-- The 'system' tenant is a new, hidden tenant reserved for platform-ops
-- users — the SaaS operator's own staff who manage tenants, see
-- cross-tenant analytics, and run break-glass administration. Unlike
-- business tenants ('demo', 'acme', ...) which scope their data via
-- core_auth_user.tenant_id, the system tenant's users carry tid='system'
-- in their JWT and the MyBatis-Plus interceptor recognises that as
-- "bypass scoping" (MybatisPlusConfig). With the bypass they can SELECT
-- across all tenants — necessary for the eventual tenant-management UI.
--
-- The platform admin's authority comes from holding the PLATFORM_ADMIN
-- role, which carries the 'platform:*' wildcard permission. This is the
-- system tenant's analogue of SUPER_ADMIN under business tenants —
-- both grant their respective "do anything inside this scope" power,
-- but PLATFORM_ADMIN's scope is the whole platform whereas SUPER_ADMIN
-- is scoped to one business tenant.
--
-- The 'platform:*' wildcard is intentionally distinct from '*:*'.
-- A regular tenant SUPER_ADMIN ('*:*') can do anything WITHIN their
-- tenant but cannot reach 'platform:tenant:create'; conversely, a
-- PLATFORM_ADMIN ('platform:*') can manage tenants but not impersonate
-- business users. The wildcards don't shadow each other.
--
-- Idempotent — ON CONFLICT DO NOTHING.

INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in)
VALUES ('00000000000000000000PERM50', 'system', 'platform:*', 'Platform Super',
        'platform', '*', 'platform', 1)
ON CONFLICT DO NOTHING;

INSERT INTO core_rbac_role (id, tenant_id, name, description, data_scope, is_built_in)
VALUES ('00000000000000000000ROLE50', 'system', 'Platform Admin',
        'Cross-tenant platform operator. Holds platform:* — can manage tenants and other platform-level concerns; CANNOT impersonate business-tenant users.',
        1, 1)
ON CONFLICT DO NOTHING;

INSERT INTO core_rbac_role_permission (id, tenant_id, role_id, permission_id)
VALUES ('00000000000000000000RPM050', 'system',
        '00000000000000000000ROLE50',
        '00000000000000000000PERM50')
ON CONFLICT DO NOTHING;
