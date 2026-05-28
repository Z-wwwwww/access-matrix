-- V33: belt-and-suspenders DROP of the three global-unique indexes V20
-- already intended to remove.
--
-- V2 originally created:
--   uk_core_auth_user_username      UNIQUE (username)         WHERE mark=1
--   uk_core_auth_user_email         UNIQUE (email)            WHERE mark=1 AND email IS NOT NULL
--   uk_core_auth_user_user_no       UNIQUE (user_no)          WHERE mark=1 AND user_no IS NOT NULL
--
-- V20 replaced them with per-tenant siblings:
--   uk_core_auth_user_tenant_username, uk_core_auth_user_tenant_email,
--   uk_core_auth_user_tenant_user_no
-- and ran DROP INDEX IF EXISTS on the global trio. On some environments
-- (the index names existed but the DROPs no-op'd for reasons we haven't
-- pinned down — possibly different create-time / schema-cache state) the
-- global indexes are still alive in the live DB even though Flyway
-- recorded V20 as success.
--
-- The symptom: SystemAdminSeeder's ops user (tenant=system, user_no
-- =U00000001) collides on the global uk_core_auth_user_user_no with
-- LocalAdminSeeder's demo-admin (tenant=demo, user_no=U00000001), and
-- the backend refuses to start in @Profile("local"). Same hazard exists
-- for `username` once any two tenants share an admin/ops name.
--
-- This migration is purely defensive: IF EXISTS makes it a no-op on
-- already-clean environments. After this, ALL uniqueness on
-- core_auth_user is per-(tenant, *), which is what the multi-tenant
-- design has assumed since V20.

DROP INDEX IF EXISTS uk_core_auth_user_username;
DROP INDEX IF EXISTS uk_core_auth_user_email;
DROP INDEX IF EXISTS uk_core_auth_user_user_no;
