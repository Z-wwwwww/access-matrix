-- Align all time columns with the instant-based timezone model:
-- timestamp (zone-less wall clock, meaning depended on the writing JVM's
-- default zone) -> timestamptz (unambiguous instant, normalized to UTC
-- internally by PostgreSQL).
--
-- Legacy values are interpreted as UTC wall clock (USING ... AT TIME ZONE
-- 'UTC'), which is deterministic regardless of the migrating session's
-- TimeZone. Rationale: every pre-V58 environment wrote these columns via
-- LocalDateTime.now() on a UTC-default JVM, and seed rows written by earlier
-- migrations used CURRENT_TIMESTAMP in a UTC session. On a fresh database
-- this migration runs right after those seeds in the same flyway session, so
-- the same interpretation holds (postgres containers default to UTC).
--
-- Column defaults are dropped before the type change and recreated as now()
-- to avoid PostgreSQL re-wrapping the old default in a session-dependent
-- double cast (CURRENT_TIMESTAMP::timestamp::timestamptz).
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.table_name, c.column_name, c.column_default
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema AND t.table_name = c.table_name
        WHERE c.table_schema = 'public'
          AND t.table_type = 'BASE TABLE'
          AND c.data_type = 'timestamp without time zone'
          AND c.table_name <> 'flyway_schema_history'
    LOOP
        IF r.column_default IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I DROP DEFAULT',
                           r.table_name, r.column_name);
        END IF;
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN %I TYPE timestamptz USING %I AT TIME ZONE ''UTC''',
            r.table_name, r.column_name, r.column_name);
        IF r.column_default IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I SET DEFAULT now()',
                           r.table_name, r.column_name);
        END IF;
    END LOOP;
END $$;
