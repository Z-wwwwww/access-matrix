-- V25: rename tenant_id='default' → 'demo' across every tenant-scoped table.
--
-- WHY
-- The literal value "default" used to play three overlapping roles in this
-- codebase:
--   1. Fallback tenant when no X-Tenant-Id header was supplied
--   2. Platform-ops staging ground (LocalAdminSeeder seeded the 'admin'
--      user here, LocalKeycloakAdminSeeder seeded the matching KC user)
--   3. Dev / QA demo data tenant (DemoSeeder + 5 sample users)
--
-- All three are being separated:
--   - Platform ops moves to a new 'system' realm (handled in a follow-up PR)
--   - The fallback gets dropped (later: missing X-Tenant-Id becomes a 400)
--   - The dev demo tenant is what this rename keeps — explicitly named 'demo'
--     so the next person reading the codebase understands it is sample data,
--     not a magic platform tenant.
--
-- This migration only touches tenant_id VALUES, not the DEFAULT clauses on
-- the columns. The DEFAULT clauses are effectively dead code (every INSERT
-- path supplies tenant_id explicitly via MyBatis-Plus); cleaning them up is
-- a separate cosmetic pass.
--
-- Idempotent — UPDATE ... WHERE tenant_id='default' is a no-op when the
-- migration has already run.

UPDATE core_auth_user            SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_auth_login_log       SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_numbering_key        SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_rbac_role            SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_rbac_permission      SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_rbac_user_role       SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_rbac_role_permission SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_rbac_menu            SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_rbac_role_menu       SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_rbac_dept            SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_rbac_role_dept       SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_oplog                SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_user_invite          SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE core_password_reset_token SET tenant_id = 'demo' WHERE tenant_id = 'default';
UPDATE demo_task                 SET tenant_id = 'demo' WHERE tenant_id = 'default';
