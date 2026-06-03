-- V37: core_job — scheduled-task config (the editable half of the dynamic scheduler).
--
-- WHY THIS TABLE EXISTS
-- ---------------------
-- Spring's static @Scheduled bakes cron into the annotation: you cannot change
-- the run time, start/stop a single job, or run-now at runtime. This table is
-- the config layer of a dynamic scheduler. Job LOGIC stays in code (beans
-- implementing ScheduledJob); this table stores only the adjustable config
-- (cron / enabled / concurrency). On startup JobRegistrySyncGuard upserts one
-- row per code job (mirrors how PermissionConsistencyGuard syncs permission
-- codes). The admin UI edits cron / enabled / run-now against these rows.
--
-- Scope (PLATFORM vs TENANT)
-- --------------------------
--   PLATFORM (scope=1): one row, tenant_id='system', runs under the system
--     tenant (cross-tenant, like OutboxDispatcher).
--   TENANT   (scope=2): one row per tenant (real tenant_id), runs per-tenant.
--
-- Tenancy: rows carry tenant_id and the table is NOT excluded from the tenant
-- interceptor — the admin UI is tenant-scoped (a tenant admin sees their own
-- rows; platform 'system' admin bypasses scoping and sees all). The scheduler's
-- reconciler reads ACROSS tenants by acting as the 'system' tenant (the
-- MybatisPlusConfig.ignoreTable bypass) — same dual-read pattern as core_tenant.

CREATE TABLE IF NOT EXISTS core_job (
    id               CHAR(26)     NOT NULL PRIMARY KEY,            -- ULID
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'system',       -- 'system' for PLATFORM; real tenant for TENANT
    job_code         VARCHAR(96)  NOT NULL,                        -- ScheduledJob.code()
    name             VARCHAR(128) NOT NULL,                        -- display fallback (i18n owns the real label)
    scope            SMALLINT     NOT NULL,                        -- 1=PLATFORM 2=TENANT
    cron             VARCHAR(128) NOT NULL,                        -- 6-field Spring cron
    enabled          SMALLINT     NOT NULL DEFAULT 0,              -- 1=scheduled, 0=stopped
    concurrent       SMALLINT     NOT NULL DEFAULT 0,              -- 1=allow overlap, 0=skip while running
    max_run_seconds  INTEGER      NOT NULL DEFAULT 300,            -- lock horizon + crash-recovery window
    last_fire_time   TIMESTAMP,                                    -- last fire (any node)
    last_status      SMALLINT,                                     -- mirrors last core_job_log.status (2 ok / 3 fail)
    last_duration_ms BIGINT,
    remark           VARCHAR(512),
    mark             SMALLINT     NOT NULL DEFAULT 1,
    create_user      VARCHAR(64),
    update_user      VARCHAR(64),
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One config row per (tenant, job_code). tenant_id leads, per convention.
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_job_tenant_code
    ON core_job (tenant_id, job_code) WHERE mark = 1;

-- Reconciler hot path: "all enabled rows across tenants".
CREATE INDEX IF NOT EXISTS idx_core_job_enabled
    ON core_job (enabled) WHERE mark = 1;

COMMENT ON TABLE  core_job IS
    'Scheduled-task config (dynamic scheduler). Job logic lives in code (ScheduledJob beans); this stores adjustable cron/enabled/concurrency. Synced from code on startup.';
COMMENT ON COLUMN core_job.scope IS '1=PLATFORM (system tenant, cross-tenant run) / 2=TENANT (per-tenant row + run).';
COMMENT ON COLUMN core_job.max_run_seconds IS 'Distributed-lock hold time; also the window after which a crashed node''s lock auto-expires. Set above real runtime.';
