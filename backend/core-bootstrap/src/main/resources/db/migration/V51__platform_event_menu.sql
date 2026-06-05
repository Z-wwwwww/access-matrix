-- V51: Domain-event console menu in the platform area.
--
-- Browsing the core_domain_event outbox + redriving failed events is a
-- PLATFORM-OPS feature, gated by 'platform:event:read'. Business-tenant supers
-- (tenant:*, which does NOT match platform:*) cannot see it; only PLATFORM_ADMIN
-- (ROLE50, *:* / platform:*) can. Mirrors V45 (dict menu).
--
-- The platform menus are a single GLOBAL set and were flattened to top level in
-- V48 (parent_id = NULL, rendered as promoted sidebar leaves), so this entry is
-- inserted at top level directly. The 'platform:event:*' permission rows are
-- auto-seeded at startup by PermissionConsistencyGuard from PlatformPermissions;
-- permission_code here is just a string reference. Idempotent.

INSERT INTO core_rbac_menu
    (id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code)
VALUES
    ('00000000000000000000MENU55', NULL,
     'platform.event', 'イベント', 2,
     '/platform/events', '/platform/Event/Event', 'Network', 5,
     'platform:event:read')
ON CONFLICT DO NOTHING;

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "ドメインイベント",
  "en":    "Domain events",
  "zh_CN": "领域事件",
  "zh_TW": "領域事件",
  "ko_KR": "도메인 이벤트"
}'::jsonb WHERE id = '00000000000000000000MENU55';

INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES
    ('00000000000000000000RMM055', 'system',
     '00000000000000000000ROLE50', '00000000000000000000MENU55', 1)
ON CONFLICT DO NOTHING;
