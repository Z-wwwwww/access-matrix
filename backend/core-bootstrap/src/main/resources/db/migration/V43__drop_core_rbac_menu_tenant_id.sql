-- V43: drop core_rbac_menu.tenant_id — menus are a single GLOBAL set (V41).
--
-- After V41 every active menu lives under tenant_id='system' and no query filters
-- on it (MenuMapper / RoleMenuMapper dropped the menu-side tenant predicate; the
-- table is in TENANT_EXCLUDED_TABLES). The column is pure residue. Dropping it
-- makes core_rbac_menu a genuine global table (like core_meta): TenantSchemaGuard
-- now sees no tenant_id column + table excluded = the correct, WARN-free config.
--
-- Runs AFTER V41 (which still needs tenant_id to collapse the per-tenant copies).
-- Idempotent: IF EXISTS / IF NOT EXISTS throughout.

DROP INDEX IF EXISTS uk_core_rbac_menu_code;      -- was (tenant_id, code)
DROP INDEX IF EXISTS idx_core_rbac_menu_parent;   -- was (tenant_id, parent_id, sort_order)

ALTER TABLE core_rbac_menu DROP COLUMN IF EXISTS tenant_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_rbac_menu_code
    ON core_rbac_menu (code) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_rbac_menu_parent
    ON core_rbac_menu (parent_id, sort_order) WHERE mark = 1;
