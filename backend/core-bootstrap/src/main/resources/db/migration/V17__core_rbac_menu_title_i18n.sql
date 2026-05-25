-- V17: menu title i18n
-- Adds title_i18n jsonb column to core_rbac_menu so admins can store one
-- localized title per supported locale (ja_JP / en / zh_CN / zh_TW / ko_KR).
-- The frontend's useMenuTitle() composable picks title_i18n[currentLocale]
-- first and falls back to the existing `title` column when missing.
-- Existing rows are backfilled with their current `title` as ja_JP, since
-- the seeds in V6 / V10 are in Japanese.

ALTER TABLE core_rbac_menu
    ADD COLUMN IF NOT EXISTS title_i18n jsonb;

COMMENT ON COLUMN core_rbac_menu.title_i18n IS
    'Locale → translated title, e.g. {"ja_JP":"…","en":"…"}; takes priority over title';

-- Generic backfill: any existing row gets {ja_JP: <current title>}.
UPDATE core_rbac_menu
   SET title_i18n = jsonb_build_object('ja_JP', title)
 WHERE title_i18n IS NULL
   AND title IS NOT NULL;

-- Seed full translations for the known built-in menus shipped by V6 / V10.
-- These IDs are stable seeds, so a fresh install gets multi-language UI
-- out of the box without manual admin entry.
UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "システム管理",
  "en":    "System",
  "zh_CN": "系统管理",
  "zh_TW": "系統管理",
  "ko_KR": "시스템 관리"
}'::jsonb WHERE id = '00000000000000000000MENU01';

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "ユーザー管理",
  "en":    "Users",
  "zh_CN": "用户管理",
  "zh_TW": "使用者管理",
  "ko_KR": "사용자 관리"
}'::jsonb WHERE id = '00000000000000000000MENU02';

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "ロール管理",
  "en":    "Roles",
  "zh_CN": "角色管理",
  "zh_TW": "角色管理",
  "ko_KR": "역할 관리"
}'::jsonb WHERE id = '00000000000000000000MENU03';

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "権限管理",
  "en":    "Permissions",
  "zh_CN": "权限管理",
  "zh_TW": "權限管理",
  "ko_KR": "권한 관리"
}'::jsonb WHERE id = '00000000000000000000MENU04';

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "メニュー管理",
  "en":    "Menus",
  "zh_CN": "菜单管理",
  "zh_TW": "選單管理",
  "ko_KR": "메뉴 관리"
}'::jsonb WHERE id = '00000000000000000000MENU05';

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "部署管理",
  "en":    "Departments",
  "zh_CN": "部门管理",
  "zh_TW": "部門管理",
  "ko_KR": "부서 관리"
}'::jsonb WHERE id = '00000000000000000000MENU06';

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "操作ログ",
  "en":    "Operation Logs",
  "zh_CN": "操作日志",
  "zh_TW": "操作日誌",
  "ko_KR": "작업 로그"
}'::jsonb WHERE id = '00000000000000000000MENU07';

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "デモ",
  "en":    "Demo",
  "zh_CN": "示例",
  "zh_TW": "示例",
  "ko_KR": "데모"
}'::jsonb WHERE id = '00000000000000000000MENU90';

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "タスク",
  "en":    "Tasks",
  "zh_CN": "任务",
  "zh_TW": "任務",
  "ko_KR": "작업"
}'::jsonb WHERE id = '00000000000000000000MENU91';
