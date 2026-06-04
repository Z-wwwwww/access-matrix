-- V47: standardize the platform-console menu icons to PascalCase Lucide names.
--
-- Same bug V12 fixed for the business/system menus: the platform menus seeded by
-- V28 / V40 / V41 / V45 used short lowercase names ('shield' / 'building' /
-- 'clock' / 'menu' / 'tags'). resolveIcon() (frontend/src/utils/icon-registry.js)
-- only matches PascalCase Lucide keys (iconMap) or Ant '*Outlined' names
-- (antToLucide) — lowercase resolves to null, so nothing renders in the sidebar /
-- tabs / picker preview for the whole platform console.
--
-- Each UPDATE is gated on the original lowercase default, so any icon a platform
-- admin has already customised through the Menu admin page is preserved.

-- platform (directory) — distinct from system.role's 'Shield'
UPDATE core_rbac_menu SET icon = 'ShieldCheck'
 WHERE id = '00000000000000000000MENU50' AND (icon = 'shield' OR icon IS NULL);

-- platform.tenant
UPDATE core_rbac_menu SET icon = 'Building2'
 WHERE id = '00000000000000000000MENU51' AND (icon = 'building' OR icon IS NULL);

-- platform.job (scheduled tasks)
UPDATE core_rbac_menu SET icon = 'Clock'
 WHERE id = '00000000000000000000MENU52' AND (icon = 'clock' OR icon IS NULL);

-- platform.menu
UPDATE core_rbac_menu SET icon = 'Menu'
 WHERE id = '00000000000000000000MENU53' AND (icon = 'menu' OR icon IS NULL);

-- platform.dict
UPDATE core_rbac_menu SET icon = 'Tags'
 WHERE id = '00000000000000000000MENU54' AND (icon = 'tags' OR icon IS NULL);
