-- V27: central tenant registry table.
--
-- Until now "tenant" was a string scattered across every business table's
-- `tenant_id` column with no canonical registry. Adding new tenants meant
-- running `infra/keycloak/new-tenant.{ps1,sh}` + restarting Keycloak +
-- manually inserting per-tenant seed data. Removing one was even worse
-- (no record of what to clean up).
--
-- core_tenant gives the system tenant a place to track every business
-- tenant: when it was created, by whom, current status, contact email
-- for the customer-side admin, and a free-form meta JSONB blob for
-- billing / feature-flag / quota concerns that will land in follow-up
-- PRs.
--
-- Storage model:
--   - All rows live under tenant_id='system' (the platform-ops tenant)
--   - PLATFORM_ADMIN users (tid='system') bypass the MP tenant
--     interceptor (see MybatisPlusConfig.PLATFORM_TENANT_ID), so they
--     can SELECT/UPDATE across all rows
--   - Business-tenant users (tid='acme' etc.) would have queries
--     scoped to their own tenant_id by the interceptor, so they read
--     zero rows from here. Defense in depth alongside the matcher
--     carve-out that prevents *:* from satisfying platform:* perms.
--
-- Soft delete via mark column (existing convention). Hard delete is
-- intentionally not exposed via the management API — a tenant deletion
-- is a meaningful event that ought to be reversible for at least a
-- retention window; a separate ops task can hard-purge much later.

CREATE TABLE IF NOT EXISTS core_tenant (
    id              CHAR(26)     PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'system',
    tenant_code     VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    contact_email   VARCHAR(255),
    status          SMALLINT     NOT NULL DEFAULT 1,
    mark            SMALLINT     NOT NULL DEFAULT 1,
    meta            JSONB,
    create_user     VARCHAR(64),
    update_user     VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- tenant_code is the realm name. Globally unique among ACTIVE rows; a
-- soft-deleted row's code can be reclaimed later.
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_tenant_code
    ON core_tenant (tenant_code) WHERE mark = 1;

-- "list active tenants ordered by recency" — the platform dashboard's
-- primary query.
CREATE INDEX IF NOT EXISTS idx_core_tenant_active
    ON core_tenant (create_time DESC) WHERE mark = 1;

COMMENT ON TABLE  core_tenant IS
    'Central registry of business tenants. Rows owned by the system tenant; PLATFORM_ADMIN manages.';
COMMENT ON COLUMN core_tenant.tenant_code IS
    'The realm name in Keycloak AND the value of tenant_id on every business-tenant row. Lowercase RFC1035 label.';
COMMENT ON COLUMN core_tenant.status IS
    '1=active (users can sign in), 0=suspended (KC realm disabled — used for billing-suspended tenants).';
COMMENT ON COLUMN core_tenant.mark IS
    'Soft-delete flag. 0=deleted (row + KC realm both inert). Hard delete is a separate ops task.';
COMMENT ON COLUMN core_tenant.meta IS
    'Free-form JSONB for billing plan / feature flags / quotas. Schema-less by design — platform extensions own their keys.';

-- Seed the demo + system tenants themselves so the registry reflects
-- reality from day 1. The system tenant entry is mostly cosmetic (the
-- code already special-cases tid='system' in the MP interceptor) but
-- listing it makes "show all tenants" return a meaningful first row
-- and avoids the dashboard appearing empty on a fresh install.
INSERT INTO core_tenant (id, tenant_id, tenant_code, display_name, contact_email, status, mark, create_user)
VALUES
  ('00000000000000000000TNT001', 'system', 'system', 'Platform Operations', NULL, 1, 1, 'migration-v27'),
  ('00000000000000000000TNT002', 'system', 'demo',   'Demo Tenant',         NULL, 1, 1, 'migration-v27')
ON CONFLICT (id) DO NOTHING;
