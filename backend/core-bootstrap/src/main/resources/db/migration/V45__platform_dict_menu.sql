-- V45: Dictionary management menu in the platform console (system tenant only).
--
-- Managed-dictionary maintenance is a PLATFORM-OPS feature (global config, like
-- menu management V41). The entry lives under the platform directory (MENU50,
-- '/platform'), gated by 'platform:dict:read'. Business-tenant supers (tenant:*,
-- which does NOT match platform:*) cannot see it; only PLATFORM_ADMIN (ROLE50,
-- *:* / platform:*) can. Mirrors V40 (job) / V41 (menu).
--
-- The 'platform:dict:*' permission rows are auto-seeded at startup by
-- PermissionConsistencyGuard from PlatformPermissions; permission_code here is
-- just a string reference. Idempotent.

-- core_rbac_menu.tenant_id was dropped in V43 (menus are a single GLOBAL set), so
-- this INSERT omits it — unlike the pre-V43 V40/V41 menu migrations.
INSERT INTO core_rbac_menu
    (id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code)
VALUES
    ('00000000000000000000MENU54', '00000000000000000000MENU50',
     'platform.dict', '辞書管理', 2,
     '/platform/dicts', '/platform/Dict/Dict', 'tags', 4,
     'platform:dict:read')
ON CONFLICT DO NOTHING;

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "辞書管理",
  "en":    "Dictionaries",
  "zh_CN": "字典管理",
  "zh_TW": "字典管理",
  "ko_KR": "사전 관리"
}'::jsonb WHERE id = '00000000000000000000MENU54';

INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES
    ('00000000000000000000RMM054', 'system',
     '00000000000000000000ROLE50', '00000000000000000000MENU54', 1)
ON CONFLICT DO NOTHING;
