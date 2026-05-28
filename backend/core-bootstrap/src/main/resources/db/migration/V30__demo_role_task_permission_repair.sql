-- V30: ROLE11-15（data-scope demo の 5 ロール）が task 権限を喪失していた
--      不具合のリカバリ。
--
-- 背景:
--   V8 が built-in permission として PERM20-PERM23 を permission:read/
--   create/update/delete として投入。
--   V10 はそれを知らずに同じ ID 範囲 PERM21-PERM24 を task:read/create/
--   update/delete として INSERT しようとしたため、PERM21-23 は ON CONFLICT
--   でスキップされた（V8 の permission:* が勝つ）。PERM24 = task:delete
--   だけは衝突せずに入った。
--   V10 はさらに role_permission に ROLE11-15 → PERM21-24 のリンクを
--   生成済み。これらは intent としては task:* を指していたが、実際の
--   permission 行は permission:* を指していた。
--   起動時の PermissionConsistencyGuard が permission:create/update/delete
--   を「コード Registry に居ない孤児」として soft delete し、連動で
--   role_permission も soft delete → ROLE11-15 の task バインドが消失。
--
-- 影響:
--   - ROLE11: task:delete だけ残る（read/create/update を喪失）
--   - ROLE12-15: V10 由来の task バインドが全消失
--   → DemoSeeder で割当てた 5 名の demo ユーザーが task 機能を実質
--     使えなくなり、data-scope demo が成立しない。
--
-- 修復方針:
--   1) task:read/create/update を安定 ID (PERM25-27) で投入。Guard が
--      既にランダム ULID で投入済みの環境では partial unique index
--      uk_core_rbac_perm_code に阻まれて NO-OP（ID は不揃いになるが
--      code 基準で参照するので機能差なし）。
--   2) V10 が意図していた (role, task:*) ペアごとに role_permission 行を
--      コード基準 JOIN で新規挿入。安定 ID R3011A..R3015A を割当て。
--      既に同じ (tenant, role, permission_id, mark=1) があれば WHERE NOT
--      EXISTS で除外 → ユーザーが UI で手動付与した行を上書きしない。
--
-- 冪等: 全 INSERT が NOT EXISTS / unique index で防御されているので
--       何度走らせても結果は同じ。

-- ========== Step 1: ensure task:read/create/update exist with stable IDs ==========
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in, mark)
SELECT '00000000000000000000PERM25', 'demo', 'task:read', 'Read Task', 'task', 'read', 'demo', 1, 1
 WHERE NOT EXISTS (SELECT 1 FROM core_rbac_permission WHERE tenant_id='demo' AND code='task:read' AND mark=1);

INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in, mark)
SELECT '00000000000000000000PERM26', 'demo', 'task:create', 'Create Task', 'task', 'create', 'demo', 1, 1
 WHERE NOT EXISTS (SELECT 1 FROM core_rbac_permission WHERE tenant_id='demo' AND code='task:create' AND mark=1);

INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in, mark)
SELECT '00000000000000000000PERM27', 'demo', 'task:update', 'Update Task', 'task', 'update', 'demo', 1, 1
 WHERE NOT EXISTS (SELECT 1 FROM core_rbac_permission WHERE tenant_id='demo' AND code='task:update' AND mark=1);

-- ========== Step 2: restore V10's intended ROLE → task:* bindings ==========
-- V10 の元の対応:
--   ROLE11 (デモ：全範囲)        → task:read, task:create, task:update, task:delete  (task:delete は既存)
--   ROLE12 (デモ：自部署＋下位)  → task:read, task:create, task:update
--   ROLE13 (デモ：自部署のみ)    → task:read, task:create, task:update
--   ROLE14 (デモ：本人のみ)      → task:read, task:create
--   ROLE15 (デモ：カスタム部署)  → task:read

INSERT INTO core_rbac_role_permission (id, tenant_id, role_id, permission_id, mark, create_user, update_user)
SELECT desired.id, 'demo', desired.role_id, p.id, 1, 'migration-v30', 'migration-v30'
  FROM (VALUES
        ('00000000000000000000R3011A', '00000000000000000000ROLE11', 'task:read'),
        ('00000000000000000000R3011B', '00000000000000000000ROLE11', 'task:create'),
        ('00000000000000000000R3011C', '00000000000000000000ROLE11', 'task:update'),
        ('00000000000000000000R3012A', '00000000000000000000ROLE12', 'task:read'),
        ('00000000000000000000R3012B', '00000000000000000000ROLE12', 'task:create'),
        ('00000000000000000000R3012C', '00000000000000000000ROLE12', 'task:update'),
        ('00000000000000000000R3013A', '00000000000000000000ROLE13', 'task:read'),
        ('00000000000000000000R3013B', '00000000000000000000ROLE13', 'task:create'),
        ('00000000000000000000R3013C', '00000000000000000000ROLE13', 'task:update'),
        ('00000000000000000000R3014A', '00000000000000000000ROLE14', 'task:read'),
        ('00000000000000000000R3014B', '00000000000000000000ROLE14', 'task:create'),
        ('00000000000000000000R3015A', '00000000000000000000ROLE15', 'task:read')
       ) AS desired(id, role_id, code)
  JOIN core_rbac_permission p
    ON p.tenant_id = 'demo' AND p.code = desired.code AND p.mark = 1
 WHERE NOT EXISTS (
       SELECT 1 FROM core_rbac_role_permission existing
        WHERE existing.tenant_id = 'demo'
          AND existing.role_id = desired.role_id
          AND existing.permission_id = p.id
          AND existing.mark = 1
       );
