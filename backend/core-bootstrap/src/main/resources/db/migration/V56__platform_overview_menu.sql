-- V56: Platform overview (dashboard) menu.
--
-- WHY: the tenant-management page (MENU51) had grown a tall dashboard on top
-- (KPI cards + status/trend charts + ops-monitoring panels) that pushed the
-- actual tenant table below the fold. We split the dashboard out into its own
-- "Overview" page so the management table is visible immediately, and the
-- monitoring/KPIs get a dedicated page with full vertical space to grow.
--
-- Top-level platform leaf (parent_id = NULL, matching the V48 flattening).
-- Gated by 'platform:tenant:read' — the SAME permission both dashboard
-- endpoints (/platform/dashboard, /platform/tenants/stats) already require —
-- so visibility tracks tenant-read exactly and no new permission is needed.
-- sort_order 0 puts it first, ahead of tenant management (MENU51, order 1).
-- ULID 50-series, consistent with the other platform-console seed rows.

INSERT INTO core_rbac_menu
    (id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code)
VALUES
    ('00000000000000000000MENU57', NULL,
     'platform.overview', 'プラットフォーム概要', 2,
     '/platform/overview', '/platform/Overview/Overview', 'LayoutDashboard', 0,
     'platform:tenant:read')
ON CONFLICT DO NOTHING;

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "プラットフォーム概要",
  "en":    "Overview",
  "zh_CN": "平台总览",
  "zh_TW": "平台總覽",
  "ko_KR": "플랫폼 개요"
}'::jsonb WHERE id = '00000000000000000000MENU57';

-- Bind to PLATFORM_ADMIN (ROLE50, platform:*) so the menu fetch returns it.
INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES
    ('00000000000000000000RMM057', 'system',
     '00000000000000000000ROLE50', '00000000000000000000MENU57', 1)
ON CONFLICT DO NOTHING;
