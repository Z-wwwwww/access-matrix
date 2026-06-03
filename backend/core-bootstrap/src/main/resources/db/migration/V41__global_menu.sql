-- V41: Make core_rbac_menu a single GLOBAL menu set + move menu management to the platform console.
--
-- WHY
-- ---
-- Menus were stored per-tenant (each business tenant got a clone of the 'demo'
-- template at creation). That made navigation a per-tenant copy and forced a
-- migration + per-tenant back-fill every time a feature/page was added. Menu
-- structure is really a platform/product concern, not per-tenant business data.
--
-- This migration collapses all active menus into ONE global set (parked under
-- tenant_id='system'); the menu read queries stop filtering by tenant (see
-- MenuMapper change + core_rbac_menu added to TENANT_EXCLUDED_TABLES), so every
-- tenant shares the same navigation, filtered per user by permission_code.
-- Menu MANAGEMENT moves to the platform console (platform:menu:*); the business
-- 'system.menu' entry is retired here.
--
-- SAFETY: only soft-deletes (mark=0) and tenant_id UPDATEs — never a hard DELETE
-- — so the core_rbac_role_menu FK (ON DELETE RESTRICT) is never triggered.
-- The unique index (tenant_id, code) WHERE mark=1 is kept: with every active row
-- under 'system' it is equivalent to a global unique-on-code, and the surviving
-- codes are distinct, so no transient/again-run collision. Idempotent.

-- a. Soft-delete clones in any tenant other than 'demo' (template) / 'system' (global home),
--    plus their role_menu bindings. (acme has no bindings today; defensive + future-proof.)
UPDATE core_rbac_role_menu SET mark = 0, update_user = 'v41'
 WHERE mark = 1
   AND menu_id IN (SELECT id FROM core_rbac_menu WHERE mark = 1 AND tenant_id NOT IN ('demo', 'system'));
UPDATE core_rbac_menu SET mark = 0, update_user = 'v41'
 WHERE mark = 1 AND tenant_id NOT IN ('demo', 'system');

-- b. Retire the business "menu management" entry (MENU05 / system.menu) and its bindings —
--    menu management now lives in the platform console (step d).
UPDATE core_rbac_role_menu SET mark = 0, update_user = 'v41'
 WHERE mark = 1 AND menu_id = '00000000000000000000MENU05';
UPDATE core_rbac_menu SET mark = 0, update_user = 'v41'
 WHERE mark = 1 AND id = '00000000000000000000MENU05';

-- c. Merge demo's surviving active menus into the global 'system' home.
--    role_menu bindings reference menu_id (unchanged), so demo's data-scope roles
--    (ROLE11-15 -> demo/demo.task) keep resolving; the read path drops the
--    menu-side tenant filter, so the join still matches.
UPDATE core_rbac_menu SET tenant_id = 'system'
 WHERE tenant_id = 'demo' AND mark = 1;

-- d. Add the platform "Menu management" entry under the platform directory (MENU50),
--    bind to Platform Admin (ROLE50), with i18n. Mirrors V40 (platform job menu).
--    The platform:menu:* permission rows are auto-seeded at startup by
--    PermissionConsistencyGuard (from PlatformMenuPermissions); permission_code
--    here is just a string reference.
INSERT INTO core_rbac_menu
    (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code)
VALUES
    ('00000000000000000000MENU53', 'system', '00000000000000000000MENU50',
     'platform.menu', 'メニュー管理', 2,
     '/platform/menus', '/platform/Menu/Menu', 'menu', 3,
     'platform:menu:read')
ON CONFLICT DO NOTHING;

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "メニュー管理",
  "en":    "Menus",
  "zh_CN": "菜单管理",
  "zh_TW": "選單管理",
  "ko_KR": "메뉴 관리"
}'::jsonb WHERE id = '00000000000000000000MENU53';

INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES
    ('00000000000000000000RMM053', 'system',
     '00000000000000000000ROLE50', '00000000000000000000MENU53', 1)
ON CONFLICT DO NOTHING;
