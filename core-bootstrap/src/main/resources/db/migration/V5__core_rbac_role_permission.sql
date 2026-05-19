-- V5: RBAC core tables — role / permission / user_role / role_permission
-- Plus built-in seeds: SUPER_ADMIN role + *:* permission + auth:unlock / auth:reset-password

-- ============================================
-- Role
-- ============================================
CREATE TABLE IF NOT EXISTS core_rbac_role (
    id            CHAR(26)     PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    data_scope    SMALLINT     NOT NULL DEFAULT 4,
    is_built_in   SMALLINT     NOT NULL DEFAULT 0,
    status        SMALLINT     NOT NULL DEFAULT 1,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    mark          SMALLINT     NOT NULL DEFAULT 1,
    create_user   VARCHAR(64),
    update_user   VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_role_code
    ON core_rbac_role (tenant_id, code) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_role_status
    ON core_rbac_role (tenant_id, status) WHERE mark = 1;

COMMENT ON TABLE core_rbac_role IS 'RBAC role definitions';
COMMENT ON COLUMN core_rbac_role.code IS 'Business unique code, e.g. SUPER_ADMIN, PMS_FRONT_DESK';
COMMENT ON COLUMN core_rbac_role.data_scope IS '1=ALL 2=DEPT_AND_SUB 3=DEPT 4=SELF 5=CUSTOM (used from Stage 3)';
COMMENT ON COLUMN core_rbac_role.is_built_in IS '1=built-in, cannot be deleted';

-- ============================================
-- Permission dictionary
-- ============================================
CREATE TABLE IF NOT EXISTS core_rbac_permission (
    id            CHAR(26)     PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    code          VARCHAR(128) NOT NULL,
    name          VARCHAR(128) NOT NULL,
    resource      VARCHAR(64)  NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    module        VARCHAR(32),
    description   VARCHAR(512),
    is_built_in   SMALLINT     NOT NULL DEFAULT 0,
    mark          SMALLINT     NOT NULL DEFAULT 1,
    create_user   VARCHAR(64),
    update_user   VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_perm_code
    ON core_rbac_permission (tenant_id, code) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_perm_module
    ON core_rbac_permission (tenant_id, module) WHERE mark = 1;

COMMENT ON TABLE core_rbac_permission IS 'RBAC permission dictionary';
COMMENT ON COLUMN core_rbac_permission.code IS 'Permission string in resource:action format';
COMMENT ON COLUMN core_rbac_permission.module IS 'Owning module: system / pms / iot ...';

-- ============================================
-- User-Role
-- ============================================
CREATE TABLE IF NOT EXISTS core_rbac_user_role (
    id            CHAR(26)     PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id       CHAR(26)     NOT NULL,
    role_id       CHAR(26)     NOT NULL,
    mark          SMALLINT     NOT NULL DEFAULT 1,
    create_user   VARCHAR(64),
    update_user   VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_user_role
    ON core_rbac_user_role (tenant_id, user_id, role_id) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_user_role_user
    ON core_rbac_user_role (tenant_id, user_id) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_user_role_role
    ON core_rbac_user_role (tenant_id, role_id) WHERE mark = 1;

COMMENT ON TABLE core_rbac_user_role IS 'User-Role many-to-many';

-- ============================================
-- Role-Permission
-- ============================================
CREATE TABLE IF NOT EXISTS core_rbac_role_permission (
    id              CHAR(26)    PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    role_id         CHAR(26)    NOT NULL,
    permission_id   CHAR(26)    NOT NULL,
    mark            SMALLINT    NOT NULL DEFAULT 1,
    create_user     VARCHAR(64),
    update_user     VARCHAR(64),
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_role_perm
    ON core_rbac_role_permission (tenant_id, role_id, permission_id) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_role_perm_role
    ON core_rbac_role_permission (tenant_id, role_id) WHERE mark = 1;

COMMENT ON TABLE core_rbac_role_permission IS 'Role-Permission many-to-many';

-- ============================================
-- Built-in seeds
-- (placeholder IDs use the form 00000000000000000000XXXX## — 26 chars, not valid ULID,
--  so they cannot collide with IdGenerator output)
-- ============================================

-- Super permission
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in)
VALUES ('00000000000000000000PERM01', 'default', '*:*', 'Super Permission', '*', '*', 'system', 1)
ON CONFLICT DO NOTHING;

-- auth:unlock / auth:reset-password (back-compat with existing endpoints)
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in)
VALUES
 ('00000000000000000000PERM02', 'default', 'auth:unlock',         'Unlock User Account', 'auth', 'unlock',         'system', 1),
 ('00000000000000000000PERM03', 'default', 'auth:reset-password', 'Reset User Password', 'auth', 'reset-password', 'system', 1)
ON CONFLICT DO NOTHING;

-- Super admin role
INSERT INTO core_rbac_role (id, tenant_id, code, name, description, data_scope, is_built_in)
VALUES ('00000000000000000000ROLE01', 'default', 'SUPER_ADMIN', 'Super Administrator',
        'Built-in super admin role with *:* permission', 1, 1)
ON CONFLICT DO NOTHING;

-- Link SUPER_ADMIN -> *:*
INSERT INTO core_rbac_role_permission (id, tenant_id, role_id, permission_id)
VALUES ('00000000000000000000RPM001', 'default',
        '00000000000000000000ROLE01', '00000000000000000000PERM01')
ON CONFLICT DO NOTHING;
