-- V29: swap the super-wildcard symbols so the visual matches the privilege level.
--
-- Before                              After
-- ──────────────────────────────────  ────────────────────────────────────────
-- *:*       held by SUPER_ADMIN       *:*       held by PLATFORM_ADMIN
--   (with platform: carve-out)          (platform: namespace only)
-- platform:*    PLATFORM_ADMIN        tenant:*  held by SUPER_ADMIN
--   (platform: namespace only)          (everything except platform:)
--
-- WHY
-- The symbol "*:*" intuitively reads as "the highest-privilege wildcard."
-- Before this migration it was actually the business-tenant SUPER_ADMIN's
-- wildcard, with a carve-out preventing it from satisfying platform:
-- permissions. That worked but the visual mismatched the role hierarchy.
-- After this migration, "*:*" matches the most privileged role
-- (PLATFORM_ADMIN), and the business super-admin gets a different symbol
-- "tenant:*" that maps to "I can do anything within my tenant."
--
-- Symmetric carve-outs are preserved:
--   - *:*       matches the platform: namespace, NOT business permissions
--   - tenant:*  matches everything OUTSIDE the platform: namespace
-- Neither covers the other. The PermissionMatcher in core-common encodes
-- this; this migration just renames the literal codes to align with the
-- new semantics.
--
-- Behavior under the new matcher:
--   - SUPER_ADMIN with code='tenant:*' can do every business operation
--     (same as before — code change is cosmetic for them).
--   - PLATFORM_ADMIN with code='*:*' can do every platform operation
--     (same as before — code change is cosmetic for them too).
-- Net effect on existing data: identical behavior, clearer naming.
--
-- Idempotent — re-running is a no-op (the WHERE clauses won't match
-- after the first run because we're updating by ID).

-- SUPER_ADMIN's wildcard: *:* → tenant:*
UPDATE core_rbac_permission
   SET code = 'tenant:*',
       name = 'Tenant Super',
       resource = 'tenant',
       action = '*',
       update_user = 'migration-v29'
 WHERE id = '00000000000000000000PERM01';

-- PLATFORM_ADMIN's wildcard: platform:* → *:*
UPDATE core_rbac_permission
   SET code = '*:*',
       name = 'Platform Super',
       resource = '*',
       action = '*',
       update_user = 'migration-v29'
 WHERE id = '00000000000000000000PERM50';
