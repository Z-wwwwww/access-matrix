-- V48: flatten the platform console — drop the "プラットフォーム管理" directory
-- (MENU50) and promote its four pages (tenant / job / menu / dict) to top level.
--
-- WHY: the platform group held a single directory with four leaf pages. The product
-- now wants those pages directly at the top level of the sidebar rather than nested
-- one level deep. The frontend already renders first-level leaf menus (no children)
-- as promoted top-level buttons (AppSidebar.topLevelLeafs), so reparenting to NULL is
-- all that's needed — no component change.
--
-- SAFETY: only soft-delete (mark=0) and parent_id UPDATEs — never a hard DELETE — so
-- the core_rbac_role_menu FK (ON DELETE RESTRICT) is never triggered. The pages keep
-- their own permission_code (platform:*:read), so visibility stays gated exactly as
-- before; only their nesting changes. Idempotent.

-- a. Retire the platform directory's role binding (RMM050 -> MENU50), then the
--    directory row itself. The four child pages keep their own bindings (RMM051-054).
UPDATE core_rbac_role_menu SET mark = 0, update_user = 'v48'
 WHERE mark = 1 AND menu_id = '00000000000000000000MENU50';

UPDATE core_rbac_menu SET mark = 0, update_user = 'v48'
 WHERE mark = 1 AND id = '00000000000000000000MENU50';

-- b. Promote the four pages to top level (parent_id = NULL). sort_order is left as-is
--    (1..4) so they keep their tenant -> job -> menu -> dict order in the top-level
--    leaf section.
UPDATE core_rbac_menu SET parent_id = NULL, update_user = 'v48'
 WHERE mark = 1 AND id IN (
   '00000000000000000000MENU51',  -- platform.tenant
   '00000000000000000000MENU52',  -- platform.job
   '00000000000000000000MENU53',  -- platform.menu
   '00000000000000000000MENU54'   -- platform.dict
 );
