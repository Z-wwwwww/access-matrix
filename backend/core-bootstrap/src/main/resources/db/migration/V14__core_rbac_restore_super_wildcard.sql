-- V14: 緊急修復 — PermissionConsistencyGuard の初版が「孤児追従」で
--      通配 *:* を誤って soft delete し、SUPER_ADMIN ロールの *:* バインドも
--      切断してしまっていた問題のリカバリ。
--
-- 通配 (*:* / xxx:*) はコード常量に登録しない設計（PermissionMatcher の
-- 動的ルールで解釈する）なので、孤児扱いから除外する修正は Guard 側で実施済み。
-- 本マイグレーションでは既存 DB 上の被害を巻き戻す。
--
-- 冪等：本来削除されていない環境（fresh DB / 影響を受けていない deploy）でも
-- WHERE 条件で 0 行になるため副作用なし。

-- 1. PERM01 (*:*) を復活
UPDATE core_rbac_permission
   SET mark = 1, update_time = CURRENT_TIMESTAMP
 WHERE id = '00000000000000000000PERM01'
   AND code = '*:*'
   AND mark = 0;

-- 2. SUPER_ADMIN → *:* の role-permission バインドを復活
UPDATE core_rbac_role_permission
   SET mark = 1, update_time = CURRENT_TIMESTAMP
 WHERE id = '00000000000000000000RPM001'
   AND role_id = '00000000000000000000ROLE01'
   AND permission_id = '00000000000000000000PERM01'
   AND mark = 0;
