-- V36: core_domain_event — platform event store + transactional outbox.
--
-- WHY THIS TABLE EXISTS (foundation-stage groundwork for AI / revenue mgmt)
-- ------------------------------------------------------------------------
-- Future AI / revenue-management features are data-hungry and depend on
-- *time-series* data that CANNOT be back-filled (booking pace, pickup
-- curves, every price change with its reason). The cheapest insurance we
-- can buy now — before any business table exists — is a generic, business-
-- agnostic event pipe: every state change emits one immutable row here,
-- inside the same transaction as the business write (the "transactional
-- outbox" pattern). A later dispatcher streams these rows to an analytics
-- store / message bus. Build the pipe empty today; data accrues from the
-- first business module onward.
--
-- This is NOT core_oplog. core_oplog records *who called which HTTP
-- endpoint* (request audit). core_domain_event records *what changed in
-- the domain* (business facts: ReservationCreated, RatePriceChanged) with
-- a structured payload meant to be consumed by machines (analytics, AI,
-- projections), not read by an admin.
--
-- Insert-mostly, like core_oplog: no `mark` (events are never soft-deleted;
-- they age out via a retention/purge job), no update_user. The only
-- mutation is the outbox dispatch bookkeeping (dispatch_state / *_at), set
-- by the dispatcher, never by business code.
--
-- Tenancy: rows carry tenant_id (defense-in-depth + per-tenant replay), but
-- the outbox dispatcher is a background job that must scan ACROSS tenants.
-- It therefore reads via hand-written SQL (not the MP tenant interceptor) —
-- the same cross-tenant platform-ops pattern noted on core_tenant (V27).
-- Do NOT add this table to TENANT_EXCLUDED_TABLES: ordinary per-tenant
-- writes/queries SHOULD still be tenant-scoped by the interceptor.

CREATE TABLE IF NOT EXISTS core_domain_event (
    id                 CHAR(26)     PRIMARY KEY,                  -- ULID; sortable ≈ insertion order, used for outbox ordering
    tenant_id          VARCHAR(64)  NOT NULL DEFAULT 'default',
    aggregate_type     VARCHAR(64)  NOT NULL,                     -- 'Reservation' / 'Rate' / ... (the entity kind)
    aggregate_id       VARCHAR(64)  NOT NULL,                     -- the business entity id the event is about
    event_type         VARCHAR(96)  NOT NULL,                     -- 'reservation.created' / 'rate.price_changed'
    payload            JSONB,                                     -- structured fact, serialized via Jackson3 JsonMapper
    actor              VARCHAR(64),                               -- user id / 'system' / AI service-account client id
    actor_type         SMALLINT     NOT NULL DEFAULT 1,           -- 1 human / 2 ai / 3 system
    trace_id           VARCHAR(64),                               -- correlates with MDC traceId + core_oplog
    occurred_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- outbox dispatch bookkeeping (owned by the dispatcher, not business code)
    dispatch_state     SMALLINT     NOT NULL DEFAULT 0,           -- 0 pending / 1 dispatched / 2 failed
    dispatch_attempts  SMALLINT     NOT NULL DEFAULT 0,
    dispatched_at      TIMESTAMP
);

-- The outbox hot path: "next batch of undispatched events, in order".
-- Partial index keeps it tiny — only pending rows are indexed; dispatched
-- rows drop out, so the index size tracks backlog depth, not table size.
CREATE INDEX IF NOT EXISTS idx_core_domain_event_outbox
    ON core_domain_event (id) WHERE dispatch_state = 0;

-- Event replay / per-aggregate history: "all events for this entity, in order".
CREATE INDEX IF NOT EXISTS idx_core_domain_event_aggregate
    ON core_domain_event (tenant_id, aggregate_type, aggregate_id, occurred_at);

-- Analytics sweep: "all events of a type in a tenant over a time window".
CREATE INDEX IF NOT EXISTS idx_core_domain_event_type_time
    ON core_domain_event (tenant_id, event_type, occurred_at);

COMMENT ON TABLE  core_domain_event IS
    'Platform event store + transactional outbox. Immutable domain facts (NOT request audit — that is core_oplog). Consumed by analytics / AI / projections.';
COMMENT ON COLUMN core_domain_event.payload IS
    'Structured business fact as JSONB. Schema is per event_type, owned by the emitting module. Serialize with the Jackson3 JsonMapper bean.';
COMMENT ON COLUMN core_domain_event.actor_type IS
    '1=human user, 2=AI service account, 3=system/automated. Lets analytics separate human vs AI-driven changes.';
COMMENT ON COLUMN core_domain_event.dispatch_state IS
    '0=pending (in outbox), 1=dispatched to bus/analytics, 2=failed (retry). Set only by the outbox dispatcher.';
