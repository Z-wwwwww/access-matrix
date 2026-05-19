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
COMMENT ON COLUMN core_rbac_menu.component       IS 'relative .vue path or URL, e.g. /system/user/index';
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
-- Seed menus — using EXISTING frontend view paths so the demo just works.
--
-- Layout:
--   starryCommon/   directory  icon=home
--     dashboard       page     /starryCommon/dashboard
--     profile         page     /starryCommon/Profile
--   data/           directory  icon=database
--     city            page     /data/City
--     dictionary      page     /data/Dictionary
--   starryPms/      directory  icon=hotel
--     listingProperty page     /starryPms/ListingProperty
--     reservation     page     /starryPms/Reservation
-- ============================================

-- Top-level directories
INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, title, menu_type, path, icon, sort_order) VALUES
 ('00000000000000000000MENU01','default',NULL,'starryCommon','共通',1,'/starryCommon','home',10),
 ('00000000000000000000MENU10','default',NULL,'data',        'マスタ',1,'/data',        'database',20),
 ('00000000000000000000MENU20','default',NULL,'starryPms',   'PMS', 1,'/starryPms',   'hotel',   30)
ON CONFLICT DO NOTHING;

-- starryCommon children
INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, sort_order) VALUES
 ('00000000000000000000MENU02','default','00000000000000000000MENU01','starryCommon.dashboard','ダッシュボード',2,'/starryCommon/dashboard','/starryCommon/dashboard/dashboard','dashboard',1),
 ('00000000000000000000MENU03','default','00000000000000000000MENU01','starryCommon.profile',  'プロフィール',  2,'/starryCommon/Profile',  '/starryCommon/Profile/Profile',    'user-circle',2)
ON CONFLICT DO NOTHING;

-- data children
INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, sort_order) VALUES
 ('00000000000000000000MENU11','default','00000000000000000000MENU10','data.city',      '都市・地区',2,'/data/City',      '/data/City/City',            'map',1),
 ('00000000000000000000MENU12','default','00000000000000000000MENU10','data.dictionary','辞書管理',  2,'/data/Dictionary','/data/Dictionary/Dictionary','book',2)
ON CONFLICT DO NOTHING;

-- starryPms children
INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, sort_order) VALUES
 ('00000000000000000000MENU21','default','00000000000000000000MENU20','starryPms.listingProperty','宿泊施設',  2,'/starryPms/ListingProperty','/starryPms/ListingProperty/ListingProperty','building',1),
 ('00000000000000000000MENU22','default','00000000000000000000MENU20','starryPms.cleaningTask',   '清掃タスク',2,'/starryPms/cleaning/CleaningTask','/starryPms/cleaning/CleaningTask/CleaningTask','broom',2)
ON CONFLICT DO NOTHING;
