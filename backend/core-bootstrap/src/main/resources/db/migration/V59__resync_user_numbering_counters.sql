-- V59: re-sync the per-tenant USER numbering counters with the user_no values
--      actually in use.
--
-- WHY
-- ---
-- `PlatformUserAdminService` allocated the platform-ops user_no by hand —
-- `MAX(CAST(SUBSTRING(user_no FROM 2) AS INTEGER)) + 1` — instead of going
-- through NumberingService like every other creation path
-- (UserAdminService.create, TenantAdminService.persistNewTenant,
-- OidcJitUserService). That allocator advances nothing, so
-- `core_numbering_management.seq_id` never learned about the numbers the
-- platform-user console handed out, and the two drifted apart until they point
-- at the SAME value. Observed on a live database before this migration:
--
--   tenant_id | counter_now | next_from_counter | max_in_use | next_already_taken
--   system    |           1 | U00000002         | U00000002  | t
--
-- `uk_core_auth_user_tenant_user_no` is UNIQUE (tenant_id, user_no) WHERE
-- mark = 1 AND user_no IS NOT NULL, so the colliding allocation is a hard
-- insert failure. The victim is whichever path allocates NEXT from the counter —
-- most plausibly OidcJitUserService, when an operator created straight in the
-- Keycloak `system` realm first signs in: it guards the numbering call with
-- try/catch but NOT the insert, so the duplicate key escapes as a 500 on every
-- request that user makes.
--
-- The seeded demo rows (V15 and friends inserted user_no literals directly) put
-- `demo` in the same shape — counter at 1 while U00000015 is in use — so this
-- repair is written for EVERY tenant, not just `system`.
--
-- WHAT
-- ----
-- Lift each tenant's USER counter to at least the highest user_no it has issued.
-- GREATEST() means a counter that is already ahead is left alone, so this is
-- idempotent and safe to re-run. Only rows matching the U + digits shape are
-- considered; anything else was never produced by this format ('U[%]') and must
-- not influence the counter.
--
-- Counters are deliberately NOT reset downwards and gaps are NOT reclaimed:
-- user_no is a stable human-visible reference, so re-issuing a number that a
-- deleted user once held would be worse than leaving a hole. Soft-deleted rows
-- (mark = 0) are therefore counted too — their numbers must stay retired.

UPDATE core_numbering_management m
   SET seq_id = GREATEST(m.seq_id, u.max_seq)
  FROM (
        SELECT tenant_id,
               MAX(CAST(SUBSTRING(user_no FROM 2) AS BIGINT)) AS max_seq
          FROM core_auth_user
         WHERE user_no ~ '^U[0-9]+$'
         GROUP BY tenant_id
       ) u
 WHERE m.code_kbn = 'USER'
   AND m.tenant_id = u.tenant_id;

COMMENT ON COLUMN core_numbering_management.seq_id IS
    'Last allocated sequence value. Advanced ONLY by NumberingService (atomic UPDATE ... RETURNING); never compute the next number from MAX(business column) — see V59.';
