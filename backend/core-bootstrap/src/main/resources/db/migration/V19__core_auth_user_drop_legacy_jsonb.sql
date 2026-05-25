-- V19: drop the legacy roles / authorities JSONB columns from core_auth_user.
-- These were inline arrays from the pre-V5 era. Since V5 introduced the proper
-- RBAC link tables (core_rbac_user_role / core_rbac_role_permission), both
-- columns have been pure dead weight — no code reads them; three call sites
-- write "[]" only to satisfy the NOT NULL constraint.

ALTER TABLE core_auth_user DROP COLUMN IF EXISTS roles;
ALTER TABLE core_auth_user DROP COLUMN IF EXISTS authorities;
