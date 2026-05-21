-- V12: standardize seeded menu icons to PascalCase Lucide names.
--
-- V6 / V10 originally seeded short lowercase names ('settings' / 'user' /
-- 'shield' / 'key' / 'menu' / 'tree' / 'log' / 'tag' / 'check-square').
-- These match neither iconMap (PascalCase Lucide) nor antToLucide (Ant-style
-- *Outlined), so resolveIcon() returned null and nothing rendered in the
-- sidebar / tabs / picker preview.
--
-- Each UPDATE is gated on the original default value, so any icon a user
-- has already customised through the Menu admin page is preserved.

-- ── System group (V6) ───────────────────────────────────────────────
UPDATE core_rbac_menu SET icon = 'Settings'
 WHERE id = '00000000000000000000MENU01' AND (icon = 'settings' OR icon IS NULL);

UPDATE core_rbac_menu SET icon = 'Users'
 WHERE id = '00000000000000000000MENU02' AND (icon = 'user' OR icon IS NULL);

UPDATE core_rbac_menu SET icon = 'Shield'
 WHERE id = '00000000000000000000MENU03' AND (icon = 'shield' OR icon IS NULL);

UPDATE core_rbac_menu SET icon = 'Key'
 WHERE id = '00000000000000000000MENU04' AND (icon = 'key' OR icon IS NULL);

UPDATE core_rbac_menu SET icon = 'ListTree'
 WHERE id = '00000000000000000000MENU05' AND (icon = 'menu' OR icon IS NULL);

UPDATE core_rbac_menu SET icon = 'Building2'
 WHERE id = '00000000000000000000MENU06' AND (icon = 'tree' OR icon IS NULL);

UPDATE core_rbac_menu SET icon = 'FileClock'
 WHERE id = '00000000000000000000MENU07' AND (icon = 'log' OR icon IS NULL);

-- ── Demo group (V10) ────────────────────────────────────────────────
UPDATE core_rbac_menu SET icon = 'Sparkles'
 WHERE id = '00000000000000000000MENU90' AND (icon = 'tag' OR icon IS NULL);

UPDATE core_rbac_menu SET icon = 'ListChecks'
 WHERE id = '00000000000000000000MENU91' AND (icon = 'check-square' OR icon IS NULL);
