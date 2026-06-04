-- V42: drop the vestigial core_job.scope column.
--
-- All scheduled tasks are system-level (the platform/tenant scope distinction
-- was dropped before this feature shipped). The column was written as a constant
-- 1 by JobSeeder and never read for any logic — pure residue. Remove it.
ALTER TABLE core_job DROP COLUMN IF EXISTS scope;
