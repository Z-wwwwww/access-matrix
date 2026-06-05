-- V55: two-tier platform-ops model.
--
-- WHY
-- ---
-- "ops" (PLATFORM_ADMIN / ROLE50, platform:*) is the super operator. Staff it
-- provisions through the platform-user console must be one notch lower: full
-- platform powers (tenants / jobs / menus / dicts / events) but unable to see or
-- manage the platform-user console itself — only ops can disable/delete/reset
-- other operators.
--
-- The matcher (PermissionMatcher) makes platform:* match everything in the
-- platform: namespace, so the "manage operators" capability is deliberately put
-- in a SEPARATE namespace, opsuser:* — which neither platform:* nor the *:*
-- platform-super wildcard covers. Thus:
--   * PLATFORM_ADMIN  (ops)      = platform:*  + opsuser:*   → sees + manages operators
--   * PLATFORM_OPERATOR (staff)  = platform:*               → cannot reach opsuser:*
--
-- opsuser:read/create/update/delete are also declared in PlatformPermissions and
-- auto-seeded by PermissionConsistencyGuard; we insert them here with fixed ids
-- so we can bind them now. Idempotent (ON CONFLICT DO NOTHING).

-- 1. opsuser:* permission rows (system tenant, built-in, module=platform).
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in) VALUES
 ('00000000000000000000PERM61', 'system', 'opsuser:read',   'View Platform Users',   'opsuser', 'read',   'platform', 1),
 ('00000000000000000000PERM62', 'system', 'opsuser:create', 'Create Platform User',  'opsuser', 'create', 'platform', 1),
 ('00000000000000000000PERM63', 'system', 'opsuser:update', 'Update Platform User',  'opsuser', 'update', 'platform', 1),
 ('00000000000000000000PERM64', 'system', 'opsuser:delete', 'Delete Platform User',  'opsuser', 'delete', 'platform', 1)
ON CONFLICT DO NOTHING;

-- 2. Grant all four to PLATFORM_ADMIN (ROLE50 = the super 'ops').
INSERT INTO core_rbac_role_permission (id, tenant_id, role_id, permission_id) VALUES
 ('00000000000000000000RPM061', 'system', '00000000000000000000ROLE50', '00000000000000000000PERM61'),
 ('00000000000000000000RPM062', 'system', '00000000000000000000ROLE50', '00000000000000000000PERM62'),
 ('00000000000000000000RPM063', 'system', '00000000000000000000ROLE50', '00000000000000000000PERM63'),
 ('00000000000000000000RPM064', 'system', '00000000000000000000ROLE50', '00000000000000000000PERM64')
ON CONFLICT DO NOTHING;

-- 3. New PLATFORM_OPERATOR role (ROLE51) — regular ops, platform:* only.
INSERT INTO core_rbac_role (id, tenant_id, name, description, data_scope, is_built_in)
VALUES ('00000000000000000000ROLE51', 'system', 'Platform Operator',
        'Regular platform-ops staff. Holds platform:* (manage tenants / jobs / menus / dicts / events) but NOT opsuser:* — cannot manage other platform users. Assigned to users created via the platform-user console.',
        1, 1)
ON CONFLICT DO NOTHING;

-- 4. Bind the existing platform:* wildcard (PERM50, seeded by V26) to ROLE51.
INSERT INTO core_rbac_role_permission (id, tenant_id, role_id, permission_id)
VALUES ('00000000000000000000RPM051', 'system',
        '00000000000000000000ROLE51', '00000000000000000000PERM50')
ON CONFLICT DO NOTHING;
