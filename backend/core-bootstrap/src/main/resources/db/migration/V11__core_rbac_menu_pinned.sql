-- V11: add core_rbac_menu.pinned — admin-controlled "pin to top" flag.
-- Frontend renders pinned menus as a top section in AppSidebar, separated
-- from the rest by a horizontal divider.

ALTER TABLE core_rbac_menu
    ADD COLUMN IF NOT EXISTS pinned SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN core_rbac_menu.pinned IS '1 = pin this menu to the top of the sidebar (admin-controlled)';
