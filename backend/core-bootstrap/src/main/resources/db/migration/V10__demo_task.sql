-- V10: business-demo module — Task entity for the data-scope walkthrough.
--
-- What this file creates:
--   1. The `demo_task` table with dept_id + create_user columns (the two
--      filterable dimensions DataScopeHelper.apply rewrites against).
--   2. A KYOTO sub-department under TOKYO to make DEPT_AND_SUB visible
--      (TOKYO holder = sees TOKYO + KYOTO).
--   3. 4 permission rows (task:read / create / update / delete).
--   4. 2 menu rows ("デモ" directory + "タスク" leaf wired to task:read).
--   5. 5 demo roles — one per data_scope mode (ALL / DEPT_AND_SUB / DEPT /
--      SELF / CUSTOM) — plus role_permission and role_menu links.
--   6. role_dept binding for DEMO_CUSTOM → KYOTO (CUSTOM mode reads
--      core_rbac_role_dept).
--
-- What this file does NOT create (handled by DemoSeeder.java under @Profile("local")
-- so password BCrypt hashes are computed by the same PasswordEncoder that
-- AuthService uses, ensuring login actually works):
--   - The 5 demo user rows.
--   - The 5 user_role link rows.
--   - The 15 demo_task seed rows (15 = ALL admin-visible count).

-- ============================================
-- 1. demo_task table
-- ============================================
CREATE TABLE IF NOT EXISTS demo_task (
    id                CHAR(26)     PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL DEFAULT 'default',
    dept_id           CHAR(26)     NOT NULL,
    title             VARCHAR(256) NOT NULL,
    content           VARCHAR(2048),
    status            SMALLINT     NOT NULL DEFAULT 1,
    priority          SMALLINT     NOT NULL DEFAULT 2,
    assignee_user_id  CHAR(26),
    due_date          DATE,
    mark              SMALLINT     NOT NULL DEFAULT 1,
    create_user       VARCHAR(64),
    update_user       VARCHAR(64),
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_demo_task_dept
    ON demo_task (tenant_id, dept_id) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_demo_task_creator
    ON demo_task (tenant_id, create_user) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_demo_task_status
    ON demo_task (tenant_id, status) WHERE mark = 1;

COMMENT ON TABLE  demo_task IS 'Demo task table — exercise ground for data-scope (ALL/DEPT_AND_SUB/DEPT/SELF/CUSTOM)';
COMMENT ON COLUMN demo_task.status   IS '1=TODO 2=DOING 3=DONE 4=CANCEL';
COMMENT ON COLUMN demo_task.priority IS '1=LOW 2=MID 3=HIGH';

-- ============================================
-- 2. KYOTO sub-department (child of TOKYO)
-- ============================================
INSERT INTO core_rbac_dept (id, tenant_id, parent_id, code, name, path, level, sort_order) VALUES
 ('00000000000000000000DEPT04', 'default', '00000000000000000000DEPT02', 'KYOTO',  '京都支社',
  '/00000000000000000000DEPT01/00000000000000000000DEPT02/00000000000000000000DEPT04', 3, 1)
ON CONFLICT DO NOTHING;

-- ============================================
-- 3. Permissions
-- ============================================
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in) VALUES
 ('00000000000000000000PERM21', 'default', 'task:read',   'Read Task',   'task', 'read',   'demo', 1),
 ('00000000000000000000PERM22', 'default', 'task:create', 'Create Task', 'task', 'create', 'demo', 1),
 ('00000000000000000000PERM23', 'default', 'task:update', 'Update Task', 'task', 'update', 'demo', 1),
 ('00000000000000000000PERM24', 'default', 'task:delete', 'Delete Task', 'task', 'delete', 'demo', 1)
ON CONFLICT DO NOTHING;

-- ============================================
-- 4. Menus (directory + leaf)
-- Component path '/demo/Task/Task' is case-insensitive (matches src/views/demo/Task/Task.vue)
-- ============================================
INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, title, menu_type, path, icon, sort_order) VALUES
 ('00000000000000000000MENU90', 'default', NULL, 'demo', 'デモ', 1, '/demo', 'tag', 90)
ON CONFLICT DO NOTHING;

INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code) VALUES
 ('00000000000000000000MENU91', 'default', '00000000000000000000MENU90', 'demo.task', 'タスク', 2,
  '/demo/task', '/demo/Task/Task', 'check-square', 1, 'task:read')
ON CONFLICT DO NOTHING;

-- ============================================
-- 5. Five demo roles — one per data_scope mode
-- ============================================
-- is_built_in=0：これら 5 ロールは demo 確認のためのお試しデータ。
-- 業務確認後、運用画面からそのまま削除できる前提でフラグを立てない。
-- name は「実在しそう」な職位に揃え、業務担当者が画面上で見て違和感を持たないようにする。
INSERT INTO core_rbac_role (id, tenant_id, code, name, description, data_scope, is_built_in) VALUES
 ('00000000000000000000ROLE11', 'default', 'DEMO_ALL',      '取締役',         '全社のタスクを閲覧可能',                              1, 0),
 ('00000000000000000000ROLE12', 'default', 'DEMO_DEPT_SUB', '東京支社長',     '東京支社と配下の京都支社のタスクを閲覧',              2, 0),
 ('00000000000000000000ROLE13', 'default', 'DEMO_DEPT',     '大阪支社課長',   '大阪支社のタスクのみ閲覧',                            3, 0),
 ('00000000000000000000ROLE14', 'default', 'DEMO_SELF',     '一般社員',       '自分が作成したタスクのみ閲覧',                        4, 0),
 ('00000000000000000000ROLE15', 'default', 'DEMO_CUSTOM',   '京都連絡担当',   '京都支社のタスクのみ閲覧（カスタム範囲）',            5, 0)
ON CONFLICT DO NOTHING;

-- ============================================
-- 5b. role-permission (DEMO_ALL gets all 4; others read+create+update; DEMO_SELF read+create; DEMO_CUSTOM read only)
-- ============================================
INSERT INTO core_rbac_role_permission (id, tenant_id, role_id, permission_id) VALUES
 ('00000000000000000000RP11A', 'default', '00000000000000000000ROLE11', '00000000000000000000PERM21'),
 ('00000000000000000000RP11B', 'default', '00000000000000000000ROLE11', '00000000000000000000PERM22'),
 ('00000000000000000000RP11C', 'default', '00000000000000000000ROLE11', '00000000000000000000PERM23'),
 ('00000000000000000000RP11D', 'default', '00000000000000000000ROLE11', '00000000000000000000PERM24'),

 ('00000000000000000000RP12A', 'default', '00000000000000000000ROLE12', '00000000000000000000PERM21'),
 ('00000000000000000000RP12B', 'default', '00000000000000000000ROLE12', '00000000000000000000PERM22'),
 ('00000000000000000000RP12C', 'default', '00000000000000000000ROLE12', '00000000000000000000PERM23'),

 ('00000000000000000000RP13A', 'default', '00000000000000000000ROLE13', '00000000000000000000PERM21'),
 ('00000000000000000000RP13B', 'default', '00000000000000000000ROLE13', '00000000000000000000PERM22'),
 ('00000000000000000000RP13C', 'default', '00000000000000000000ROLE13', '00000000000000000000PERM23'),

 ('00000000000000000000RP14A', 'default', '00000000000000000000ROLE14', '00000000000000000000PERM21'),
 ('00000000000000000000RP14B', 'default', '00000000000000000000ROLE14', '00000000000000000000PERM22'),

 ('00000000000000000000RP15A', 'default', '00000000000000000000ROLE15', '00000000000000000000PERM21')
ON CONFLICT DO NOTHING;

-- ============================================
-- 5c. role-menu (all 5 demo roles see the demo directory + task leaf)
-- ============================================
INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id) VALUES
 ('00000000000000000000RM11A', 'default', '00000000000000000000ROLE11', '00000000000000000000MENU90'),
 ('00000000000000000000RM11B', 'default', '00000000000000000000ROLE11', '00000000000000000000MENU91'),
 ('00000000000000000000RM12A', 'default', '00000000000000000000ROLE12', '00000000000000000000MENU90'),
 ('00000000000000000000RM12B', 'default', '00000000000000000000ROLE12', '00000000000000000000MENU91'),
 ('00000000000000000000RM13A', 'default', '00000000000000000000ROLE13', '00000000000000000000MENU90'),
 ('00000000000000000000RM13B', 'default', '00000000000000000000ROLE13', '00000000000000000000MENU91'),
 ('00000000000000000000RM14A', 'default', '00000000000000000000ROLE14', '00000000000000000000MENU90'),
 ('00000000000000000000RM14B', 'default', '00000000000000000000ROLE14', '00000000000000000000MENU91'),
 ('00000000000000000000RM15A', 'default', '00000000000000000000ROLE15', '00000000000000000000MENU90'),
 ('00000000000000000000RM15B', 'default', '00000000000000000000ROLE15', '00000000000000000000MENU91')
ON CONFLICT DO NOTHING;

-- ============================================
-- 6. role-dept (DEMO_CUSTOM only — bound to KYOTO)
-- ============================================
INSERT INTO core_rbac_role_dept (id, tenant_id, role_id, dept_id) VALUES
 ('00000000000000000000RD15A', 'default', '00000000000000000000ROLE15', '00000000000000000000DEPT04')
ON CONFLICT DO NOTHING;
