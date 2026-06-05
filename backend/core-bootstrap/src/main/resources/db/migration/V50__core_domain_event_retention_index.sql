-- V50: supporting index for the outbox retention/purge job (OutboxRetentionJob).
--
-- The purge deletes successfully-dispatched events older than the retention
-- window: WHERE dispatch_state = 1 AND dispatched_at < <cutoff>. A partial index
-- on dispatched_at restricted to dispatch_state = 1 lets that sweep find the
-- aged rows without scanning the whole table — and, being partial, it only
-- indexes dispatched rows, staying small. Mirrors V36's partial outbox index
-- (which covers the dispatcher's dispatch_state = 0 poll).

CREATE INDEX IF NOT EXISTS idx_core_domain_event_dispatched
    ON core_domain_event (dispatched_at) WHERE dispatch_state = 1;
