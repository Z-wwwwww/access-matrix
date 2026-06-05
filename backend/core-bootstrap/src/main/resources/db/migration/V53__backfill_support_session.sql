-- V53: backfill historical support sessions into core_support_session (V52).
--
-- Before V52 the dashboard listed support sessions straight from core_oplog
-- ('tenant.impersonate.start'); V52 switched the source to the new table, which
-- started empty — so existing history "disappeared" from the panel. This one-off
-- backfill rebuilds those rows from the oplog audit trail.
--
-- The oplog row has no target_id (the @OpLog aspect doesn't set it), but its
-- request_body is the serialized controller args: ["<tenantRegistryId>", {"reason": "..."}].
-- So we extract the tenant registry id from element 0 and the reason from element 1.
-- These are all past sessions, so expires_at is in the past and ended_at is set to
-- expiry — they count as ENDED, never as "active". Idempotent (id = oplog id, ON CONFLICT).
-- On a fresh install there is no such history, so this is simply a no-op.

INSERT INTO core_support_session
    (id, tenant_id, tenant_code, operator, reason, started_at, expires_at, ended_at)
SELECT o.id,
       'system',
       COALESCE(t.tenant_code, '(deleted)'),
       o.username,
       (o.request_body::jsonb -> 1 ->> 'reason'),
       o.create_time,
       o.create_time + INTERVAL '30 minutes',
       o.create_time + INTERVAL '30 minutes'
FROM core_oplog o
LEFT JOIN core_tenant t ON t.id = (o.request_body::jsonb ->> 0)
WHERE o.module = 'platform'
  AND o.action = 'tenant.impersonate.start'
  AND o.request_body ~ '^\s*\['
ON CONFLICT (id) DO NOTHING;
