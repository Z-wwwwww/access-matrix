-- V39: core_job_lock — ShedLock-style distributed lock for the dynamic scheduler.
--
-- WHY THIS TABLE EXISTS (multi-instance / clustered deployment)
-- ------------------------------------------------------------
-- In a cluster every node schedules the same CronTrigger and fires at ~the same
-- instant. Without a lock, a job would run once PER NODE. At fire time every
-- node races to acquire a row here keyed by "{job_code}::{tenant_id}"; exactly
-- one wins (atomic INSERT ... ON CONFLICT ... WHERE lock_until <= now) and runs;
-- the losers skip. A crashed holder's lock auto-expires at lock_until (= now +
-- job's max_run_seconds), so the next fire can take it over.
--
-- Hand-rolled (not the ShedLock library) to match the project's low-dependency
-- style — accessed only via hand-written SQL in JobLockMapper.
--
-- GLOBAL table: no tenant_id (the lock name already encodes the tenant), no
-- mark/audit columns. It is therefore added to
-- MybatisPlusConfig.TENANT_EXCLUDED_TABLES — required so TenantSchemaGuard does
-- not fail the boot on a table lacking tenant_id.

CREATE TABLE IF NOT EXISTS core_job_lock (
    lock_name  VARCHAR(255) NOT NULL PRIMARY KEY,   -- "{job_code}::{tenant_id}"
    locked_at  TIMESTAMP    NOT NULL,
    lock_until TIMESTAMP    NOT NULL,               -- holder's lease expiry; expired ⇒ stealable
    locked_by  VARCHAR(96)  NOT NULL                -- node_id of the current holder
);

COMMENT ON TABLE core_job_lock IS
    'Distributed lock for the dynamic scheduler (one fire runs on exactly one node). Global table — lock_name encodes the tenant; excluded from the tenant interceptor.';
