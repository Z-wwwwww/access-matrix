-- V28: menu entries for the platform-ops console.
--
-- These menus live under tenant_id='system' so only platform-ops users
-- (whose JWT carries tid='system') see them. Business-tenant users see
-- the menus seeded by V6 under their own tenant_id; the two sets are
-- disjoint.
--
-- ULIDs 50-series — kept distinct from the 01-series used by the
-- business-tenant SUPER_ADMIN's menus / roles so a future audit can
-- tell at a glance which seed the row came from.

-- Top-level group for the platform console. menu_type 1 = directory.
INSERT INTO core_rbac_menu
    (id, tenant_id, parent_id, code, title, menu_type, path, icon, sort_order)
VALUES
    ('00000000000000000000MENU50', 'system', NULL,
     'platform', 'プラットフォーム管理', 1, '/platform', 'shield', 100)
ON CONFLICT DO NOTHING;

-- Tenant management entry under it. menu_type 2 = leaf (renders a route).
INSERT INTO core_rbac_menu
    (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code)
VALUES
    ('00000000000000000000MENU51', 'system', '00000000000000000000MENU50',
     'platform.tenant', 'テナント管理', 2,
     '/platform/tenants', '/platform/Tenant/Tenant', 'building', 1,
     'platform:tenant:read')
ON CONFLICT DO NOTHING;

-- Bind the platform menus to PLATFORM_ADMIN so the menu fetch returns
-- them. core_rbac_role_menu rows are role-scoped; without this binding
-- the menu rows exist but no role can see them, and the frontend would
-- render an empty platform sidebar.
INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES
    ('00000000000000000000RMM050', 'system',
     '00000000000000000000ROLE50', '00000000000000000000MENU50', 1),
    ('00000000000000000000RMM051', 'system',
     '00000000000000000000ROLE50', '00000000000000000000MENU51', 1)
ON CONFLICT DO NOTHING;

-- The i18n title for the two menu entries. The system menus use the
-- same menu_title_i18n column V17 added, so the language switcher
-- picks the right label per the user's locale.
UPDATE core_rbac_menu
   SET title_i18n = '{"ja_JP":"プラットフォーム管理","en":"Platform","zh_CN":"平台管理","zh_TW":"平台管理","ko_KR":"플랫폼 관리"}'::jsonb
 WHERE id = '00000000000000000000MENU50';

UPDATE core_rbac_menu
   SET title_i18n = '{"ja_JP":"テナント管理","en":"Tenants","zh_CN":"租户管理","zh_TW":"租戶管理","ko_KR":"테넌트 관리"}'::jsonb
 WHERE id = '00000000000000000000MENU51';

-- The 'platform:tenant:read' permission also needs a row in
-- core_rbac_permission for the permission consistency guard to be
-- happy at startup. V26 seeded the platform:* wildcard but PermissionRegistry's
-- consistency check enumerates concrete codes too — explicitly listing
-- platform:tenant:* keeps the guard quiet and gives the permission UI
-- something concrete to display.
INSERT INTO core_rbac_permission
    (id, tenant_id, code, name, resource, action, module, is_built_in)
VALUES
    ('00000000000000000000PERM51', 'system', 'platform:tenant:read',
     'List Tenants', 'platform:tenant', 'read', 'platform', 1),
    ('00000000000000000000PERM52', 'system', 'platform:tenant:create',
     'Create Tenant', 'platform:tenant', 'create', 'platform', 1),
    ('00000000000000000000PERM53', 'system', 'platform:tenant:delete',
     'Soft-Delete Tenant', 'platform:tenant', 'delete', 'platform', 1)
ON CONFLICT DO NOTHING;
