-- V38: core_job_log — execution history for the dynamic scheduler.
--
-- One row per attempted run (cron-fired or manual). Inserted RUNNING when a
-- node wins the lock and starts, then finalized to SUCCESS/FAIL with duration
-- and (on failure) a truncated error. The admin UI's "execution history" view
-- reads this. Append-only audit of "what ran, when, how it went" — distinct
-- from core_oplog (who called which HTTP endpoint).
--
-- Tenancy: tenant_id is the tenant the run executed UNDER (PLATFORM jobs write
-- 'system'). NOT excluded from the interceptor, so the admin log query is
-- auto-scoped: a tenant admin sees their tenant's runs; the 'system' admin sees
-- all. The execution wrapper writes each row under the run's tenant context, so
-- AuditMetaObjectHandler stamps tenant_id correctly.

CREATE TABLE IF NOT EXISTS core_job_log (
    id           CHAR(26)     NOT NULL PRIMARY KEY,                -- ULID; sortable ≈ chronological
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'system',
    job_code     VARCHAR(96)  NOT NULL,
    trigger_type SMALLINT     NOT NULL,                            -- 1=cron 2=manual 3=startup
    status       SMALLINT     NOT NULL,                            -- 1=running 2=success 3=fail 4=skipped
    node_id      VARCHAR(96),                                      -- instance that won the lock and ran it
    start_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time     TIMESTAMP,
    duration_ms  BIGINT,
    error        TEXT,                                             -- truncated message/stack on fail
    triggered_by VARCHAR(64),                                      -- userId for manual runs; null for cron
    mark         SMALLINT     NOT NULL DEFAULT 1,
    create_user  VARCHAR(64),
    update_user  VARCHAR(64),
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Admin log list: "this tenant's runs of this job, newest first" (id is ULID = time-ordered).
CREATE INDEX IF NOT EXISTS idx_core_job_log_lookup
    ON core_job_log (tenant_id, job_code, id);

-- Concurrency-guard probe: "is there a RUNNING row for this tenant+code?"
CREATE INDEX IF NOT EXISTS idx_core_job_log_running
    ON core_job_log (tenant_id, job_code) WHERE status = 1 AND mark = 1;

COMMENT ON TABLE  core_job_log IS
    'Scheduled-task execution history (one row per run). Append-only. NOT request audit — that is core_oplog.';
COMMENT ON COLUMN core_job_log.status IS '1=running 2=success 3=fail 4=skipped(lock lost / concurrent guard).';
