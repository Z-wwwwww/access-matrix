-- V40: Job management menu in the platform console (system tenant only).
--
-- Scheduled-task management is a PLATFORM-OPS feature, not a per-tenant business
-- feature — all jobs are system-level. The menu therefore lives under the
-- platform console directory (MENU50, '/platform', system tenant), gated by the
-- platform-namespace permission 'platform:job:read'. Business-tenant super
-- admins (tenant:* — which does NOT match platform:*) cannot see it; only the
-- platform admin (ROLE50, *:* / platform:*) can. Mirrors V28 (platform tenant menu).
--
-- The 'platform:job:*' permission rows themselves are auto-seeded at startup by
-- PermissionConsistencyGuard from JobPermissions; the permission_code below is
-- just a string reference and does not require the row to exist at migrate time.

INSERT INTO core_rbac_menu
    (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code)
VALUES
    ('00000000000000000000MENU52', 'system', '00000000000000000000MENU50',
     'platform.job', 'ジョブ管理', 2,
     '/platform/jobs', '/platform/Job/Job', 'clock', 2,
     'platform:job:read')
ON CONFLICT DO NOTHING;

UPDATE core_rbac_menu SET title_i18n = '{
  "ja_JP": "スケジュールタスク",
  "en":    "Scheduled Tasks",
  "zh_CN": "定时任务",
  "zh_TW": "定時任務",
  "ko_KR": "예약 작업"
}'::jsonb WHERE id = '00000000000000000000MENU52';

-- Bind to Platform Admin role. ROLE50 holds *:* so MenuQueryService.findAllVisible
-- already returns it, but the explicit binding keeps parity with V28 and supports
-- any future non-super platform role.
INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES
    ('00000000000000000000RMM052', 'system',
     '00000000000000000000ROLE50', '00000000000000000000MENU52', 1)
ON CONFLICT DO NOTHING;
