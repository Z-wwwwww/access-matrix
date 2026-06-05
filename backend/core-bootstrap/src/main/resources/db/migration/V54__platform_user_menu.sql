-- V54: Platform-ops user console menu.
--
-- Manages the staff accounts in the 'system' tenant (PLATFORM_ADMIN holders).
-- Gated by 'platform:user:read'; only PLATFORM_ADMIN (ROLE50, platform:*) sees
-- it. Top-level platform leaf (parent_id = NULL, flattened in V48). Mirrors V51
-- (event menu). Gated by 'opsuser:read' — a namespace OUTSIDE 'platform:' so the
-- regular PLATFORM_OPERATOR role (platform:* only) does NOT see this menu; only
-- the super 'ops' (which also holds opsuser:*) does. See V55 for the roles.

INSERT INTO core_rbac_menu
    (id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code)
VALUES
    ('00000000000000000000MENU56', NULL,
     'platform.user', '運維ユーザー', 2,
     '/platform/users', '/platform/PlatformUser/PlatformUser', 'UserRoundCog', 6,
     'opsuser:read')
ON CONFLICT DO NOTHING;

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "運用ユーザー",
  "en":    "Platform users",
  "zh_CN": "平台用户",
  "zh_TW": "平台使用者",
  "ko_KR": "플랫폼 사용자"
}'::jsonb WHERE id = '00000000000000000000MENU56';

INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES
    ('00000000000000000000RMM056', 'system',
     '00000000000000000000ROLE50', '00000000000000000000MENU56', 1)
ON CONFLICT DO NOTHING;
