-- V13: 「権限管理画面」廃止に伴う後始末
-- ============================================
-- 注意：権限字典の不要 code (permission:create/update/delete) の soft delete は
-- アプリ起動時の PermissionConsistencyGuard が「孤児追従」として自動処理する
-- ため、本マイグレーションでは行わない。ここではメニュー側の片付けだけを行う
-- （Guard は core_rbac_menu / core_rbac_role_menu を管理対象外）。

-- 「権限管理」メニュー本体を soft delete
UPDATE core_rbac_menu
   SET mark = 0, update_time = CURRENT_TIMESTAMP
 WHERE code = 'system.permission'
   AND mark = 1;

-- メニューに紐づく role-menu も併せて soft delete
UPDATE core_rbac_role_menu
   SET mark = 0, update_time = CURRENT_TIMESTAMP
 WHERE menu_id IN (
        SELECT id FROM core_rbac_menu WHERE code = 'system.permission'
   )
   AND mark = 1;
