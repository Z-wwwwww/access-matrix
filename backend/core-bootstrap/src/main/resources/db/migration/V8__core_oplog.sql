-- V8: Operation log + management-side permission dictionary entries.
-- core_oplog rows are insert-only; no mark/update_time/audit-stamp tracking.

CREATE TABLE IF NOT EXISTS core_oplog (
    id            CHAR(26)     PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id       CHAR(26),
    username      VARCHAR(64),
    module        VARCHAR(32),
    action        VARCHAR(64)  NOT NULL,
    target_type   VARCHAR(32),
    target_id     VARCHAR(64),
    request_uri   VARCHAR(512),
    method        VARCHAR(8),
    client_ip     VARCHAR(64),
    user_agent    VARCHAR(512),
    request_body  TEXT,
    success       BOOLEAN      NOT NULL,
    error_msg     VARCHAR(512),
    cost_ms       INTEGER,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_core_oplog_user_time
    ON core_oplog (user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_core_oplog_target
    ON core_oplog (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_core_oplog_module_time
    ON core_oplog (module, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_core_oplog_tenant_time
    ON core_oplog (tenant_id, create_time DESC);

COMMENT ON TABLE  core_oplog IS 'Operation audit log (insert-only, partition by month after 6mo)';
COMMENT ON COLUMN core_oplog.request_body IS 'Request body, truncated to 4KB, password fields auto-masked to ***';

-- ============================================
-- Built-in permissions for admin endpoints (Stage 4 CRUD).
-- ============================================
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in) VALUES
 ('00000000000000000000PERM10', 'default', 'role:read',       'List/View Roles',     'role',       'read',   'system', 1),
 ('00000000000000000000PERM11', 'default', 'role:create',     'Create Role',         'role',       'create', 'system', 1),
 ('00000000000000000000PERM12', 'default', 'role:update',     'Edit Role',           'role',       'update', 'system', 1),
 ('00000000000000000000PERM13', 'default', 'role:delete',     'Delete Role',         'role',       'delete', 'system', 1),
 ('00000000000000000000PERM20', 'default', 'permission:read',   'List Permissions',  'permission', 'read',   'system', 1),
 ('00000000000000000000PERM21', 'default', 'permission:create', 'Create Permission', 'permission', 'create', 'system', 1),
 ('00000000000000000000PERM22', 'default', 'permission:update', 'Edit Permission',   'permission', 'update', 'system', 1),
 ('00000000000000000000PERM23', 'default', 'permission:delete', 'Delete Permission', 'permission', 'delete', 'system', 1),
 ('00000000000000000000PERM30', 'default', 'user:read',       'List/View Users',     'user',       'read',   'system', 1),
 ('00000000000000000000PERM31', 'default', 'user:create',     'Create User',         'user',       'create', 'system', 1),
 ('00000000000000000000PERM32', 'default', 'user:update',     'Edit User',           'user',       'update', 'system', 1),
 ('00000000000000000000PERM33', 'default', 'user:delete',     'Delete User',         'user',       'delete', 'system', 1),
 ('00000000000000000000PERM40', 'default', 'menu:read',       'List Menus',          'menu',       'read',   'system', 1),
 ('00000000000000000000PERM41', 'default', 'menu:create',     'Create Menu',         'menu',       'create', 'system', 1),
 ('00000000000000000000PERM42', 'default', 'menu:update',     'Edit Menu',           'menu',       'update', 'system', 1),
 ('00000000000000000000PERM43', 'default', 'menu:delete',     'Delete Menu',         'menu',       'delete', 'system', 1),
 ('00000000000000000000PERM51', 'default', 'dept:create',     'Create Department',   'dept',       'create', 'system', 1),
 ('00000000000000000000PERM52', 'default', 'dept:update',     'Edit Department',     'dept',       'update', 'system', 1),
 ('00000000000000000000PERM53', 'default', 'dept:delete',     'Delete Department',   'dept',       'delete', 'system', 1),
 ('00000000000000000000PERM60', 'default', 'oplog:read',      'Read Operation Log',  'oplog',      'read',   'system', 1)
ON CONFLICT DO NOTHING;
