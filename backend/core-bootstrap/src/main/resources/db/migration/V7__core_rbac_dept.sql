-- V7: Department tree + per-user dept binding + role-dept (custom data-scope) link.
-- All three are tenant-aware, soft-deleted, audit-stamped via BaseEntity convention.

-- ============================================
-- Department tree
-- ============================================
CREATE TABLE IF NOT EXISTS core_rbac_dept (
    id              CHAR(26)     PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    parent_id       CHAR(26),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    -- Materialised path "/{rootId}/{level1Id}/.../{selfId}" — leading slash, no trailing slash.
    -- Self + descendants: path = :self OR path LIKE :self || '/%'
    path            VARCHAR(1024) NOT NULL,
    level           SMALLINT     NOT NULL DEFAULT 1,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    leader_user_id  CHAR(26),
    status          SMALLINT     NOT NULL DEFAULT 1,
    mark            SMALLINT     NOT NULL DEFAULT 1,
    create_user     VARCHAR(64),
    update_user     VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_dept_code
    ON core_rbac_dept (tenant_id, code) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_dept_parent
    ON core_rbac_dept (tenant_id, parent_id) WHERE mark = 1;
-- text_pattern_ops lets LIKE 'prefix%' use the index without a collation full-scan.
CREATE INDEX IF NOT EXISTS idx_core_rbac_dept_path
    ON core_rbac_dept (tenant_id, path text_pattern_ops) WHERE mark = 1;

COMMENT ON TABLE  core_rbac_dept IS 'Department tree';
COMMENT ON COLUMN core_rbac_dept.path IS 'Materialised path /rootId/.../selfId — subtree query uses LIKE on this column';

-- ============================================
-- core_auth_user → dept binding
-- ============================================
ALTER TABLE core_auth_user ADD COLUMN IF NOT EXISTS dept_id CHAR(26);
CREATE INDEX IF NOT EXISTS idx_core_auth_user_dept
    ON core_auth_user (tenant_id, dept_id) WHERE mark = 1;

COMMENT ON COLUMN core_auth_user.dept_id IS 'Department the user belongs to (drives DEPT / DEPT_AND_SUB / SELF data scopes)';

-- ============================================
-- Role → Dept link (only used when role.data_scope = 5 CUSTOM)
-- ============================================
CREATE TABLE IF NOT EXISTS core_rbac_role_dept (
    id          CHAR(26)    PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL DEFAULT 'default',
    role_id     CHAR(26)    NOT NULL,
    dept_id     CHAR(26)    NOT NULL,
    mark        SMALLINT    NOT NULL DEFAULT 1,
    create_user VARCHAR(64),
    update_user VARCHAR(64),
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_role_dept
    ON core_rbac_role_dept (tenant_id, role_id, dept_id) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_role_dept_role
    ON core_rbac_role_dept (tenant_id, role_id) WHERE mark = 1;

COMMENT ON TABLE core_rbac_role_dept IS 'Role-Department many-to-many — only consumed when role.data_scope = 5 (CUSTOM)';

-- ============================================
-- Built-in permission for dept tree read (Stage 4 admin CRUD will add the rest)
-- ============================================
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in)
VALUES ('00000000000000000000PERM04', 'default', 'dept:read', 'Read Department Tree', 'dept', 'read', 'system', 1)
ON CONFLICT DO NOTHING;

-- ============================================
-- Seed: HQ → (Tokyo, Osaka)
-- ============================================
INSERT INTO core_rbac_dept (id, tenant_id, parent_id, code, name, path, level, sort_order) VALUES
 ('00000000000000000000DEPT01', 'default', NULL,                         'HQ',     '本社',     '/00000000000000000000DEPT01', 1, 1),
 ('00000000000000000000DEPT02', 'default', '00000000000000000000DEPT01', 'TOKYO',  '東京支社', '/00000000000000000000DEPT01/00000000000000000000DEPT02', 2, 1),
 ('00000000000000000000DEPT03', 'default', '00000000000000000000DEPT01', 'OSAKA',  '大阪支社', '/00000000000000000000DEPT01/00000000000000000000DEPT03', 2, 2)
ON CONFLICT DO NOTHING;
