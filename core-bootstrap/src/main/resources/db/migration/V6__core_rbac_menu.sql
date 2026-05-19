-- V6: RBAC menu — core_rbac_menu + core_rbac_role_menu + initial seeds
-- Column names mirror the frontend contract (ele-admin-pro style):
--   title / hide / hide_footer / hide_sidebar / tab_unique / redirect
-- so the API response can serialise straight into the menu tree the
-- frontend already consumes (formatMenus / menuToRoutes).

CREATE TABLE IF NOT EXISTS core_rbac_menu (
    id              CHAR(26)      PRIMARY KEY,
    tenant_id       VARCHAR(64)   NOT NULL DEFAULT 'default',
    parent_id       CHAR(26),
    code            VARCHAR(64)   NOT NULL,
    title           VARCHAR(128)  NOT NULL,
    menu_type       SMALLINT      NOT NULL,
    path            VARCHAR(255),
    component       VARCHAR(255),
    icon            VARCHAR(64),
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    hide            SMALLINT      NOT NULL DEFAULT 0,
    hide_footer     SMALLINT      NOT NULL DEFAULT 0,
    hide_sidebar    SMALLINT      NOT NULL DEFAULT 0,
    tab_unique      VARCHAR(64),
    redirect        VARCHAR(255),
    permission_code VARCHAR(128),
    status          SMALLINT      NOT NULL DEFAULT 1,
    mark            SMALLINT      NOT NULL DEFAULT 1,
    create_user     VARCHAR(64),
    update_user     VARCHAR(64),
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_menu_code
    ON core_rbac_menu (tenant_id, code) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_menu_parent
    ON core_rbac_menu (tenant_id, parent_id, sort_order) WHERE mark = 1;

COMMENT ON TABLE  core_rbac_menu IS 'RBAC menu / button definitions';
COMMENT ON COLUMN core_rbac_menu.menu_type       IS '1=directory 2=menu(page) 3=button';
COMMENT ON COLUMN core_rbac_menu.path            IS 'vue-router path, e.g. /system/user';
COMMENT ON COLUMN core_rbac_menu.component       IS 'relative .vue path or URL, e.g. /system/User/User';
COMMENT ON COLUMN core_rbac_menu.hide            IS '1=hide from sidebar (still participates in routing)';
COMMENT ON COLUMN core_rbac_menu.permission_code IS 'optional permission required to see this entry';

-- Role-Menu link
CREATE TABLE IF NOT EXISTS core_rbac_role_menu (
    id          CHAR(26)    PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL DEFAULT 'default',
    role_id     CHAR(26)    NOT NULL,
    menu_id     CHAR(26)    NOT NULL,
    mark        SMALLINT    NOT NULL DEFAULT 1,
    create_user VARCHAR(64),
    update_user VARCHAR(64),
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_role_menu
    ON core_rbac_role_menu (tenant_id, role_id, menu_id) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_role_menu_role
    ON core_rbac_role_menu (tenant_id, role_id) WHERE mark = 1;

COMMENT ON TABLE core_rbac_role_menu IS 'Role-Menu many-to-many';

-- ============================================
-- Seed: システム管理 directory + 6 admin pages.
-- Component paths match the frontend's resolveComponent() (case-insensitive,
-- prefix-stripped of "/src/views"), e.g. "/system/User/User" → /src/views/system/User/User.vue
-- The permission_code column scopes who sees each menu entry on top of the
-- backend's @RequiresPermission API enforcement.
-- ============================================

INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, title, menu_type, path, icon, sort_order) VALUES
 ('00000000000000000000MENU01','default',NULL,'system','システム管理',1,'/system','settings',10)
ON CONFLICT DO NOTHING;

INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, sort_order, permission_code) VALUES
 ('00000000000000000000MENU02','default','00000000000000000000MENU01','system.user',       'ユーザー管理',2,'/system/user',      '/system/User/User',             'user',     1,'user:read'),
 ('00000000000000000000MENU03','default','00000000000000000000MENU01','system.role',       'ロール管理', 2,'/system/role',      '/system/Role/Role',             'shield',   2,'role:read'),
 ('00000000000000000000MENU04','default','00000000000000000000MENU01','system.permission', '権限管理',   2,'/system/permission','/system/Permission/Permission', 'key',      3,'permission:read'),
 ('00000000000000000000MENU05','default','00000000000000000000MENU01','system.menu',       'メニュー管理',2,'/system/menu',     '/system/Menu/Menu',             'menu',     4,'menu:read'),
 ('00000000000000000000MENU06','default','00000000000000000000MENU01','system.dept',       '部署管理',   2,'/system/dept',      '/system/Dept/Dept',             'tree',     5,'dept:read'),
 ('00000000000000000000MENU07','default','00000000000000000000MENU01','system.oplog',      '操作ログ',   2,'/system/oplog',     '/system/OpLog/OpLog',           'log',      6,'oplog:read')
ON CONFLICT DO NOTHING;
