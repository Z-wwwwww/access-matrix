package com.platform.core.infrastructure.event;

import com.platform.core.common.scheduling.JobContext;
import com.platform.core.common.scheduling.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention / purge for the {@code core_domain_event} outbox, implemented as a
 * managed {@link ScheduledJob} so it shows up in the platform Job console — with
 * start/stop, cron editing, run-now, execution logging and a distributed lock,
 * all provided by {@code JobExecutionWrapper}. (The 5-second-poll
 * {@link OutboxDispatcher} deliberately stays a plain {@code @Scheduled} task —
 * its cadence is finer than the cron-based job model.)
 *
 * <p>Deletes events that have been <b>successfully dispatched</b>
 * ({@code dispatch_state = 1}) and are older than the retention window. Pending
 * ({@code 0}) and failed ({@code 2}) rows are <b>never</b> touched — undelivered
 * events must survive for retry / redrive and ops triage regardless of age. The
 * durable record is the row, and {@link OutboxDispatcher} never deletes; this is
 * the "age-out" job the {@code core_domain_event} design always assumed. Long-
 * term history belongs in the downstream analytics store, not this OLTP table.
 *
 * <p>Deletes in bounded batches (Postgres has no {@code DELETE ... LIMIT}, so it
 * targets a capped id sub-select and loops) and uses raw {@link JdbcTemplate} so
 * the MyBatis-Plus tenant interceptor is bypassed — a cross-tenant platform-ops
 * sweep, like the dispatcher.
 *
 * <p>Config (all optional):
 * <ul>
 *   <li>{@code app.outbox.retention-days} (default 30) — window; {@code <= 0} makes
 *       the job a no-op without having to disable it in the console.</li>
 *   <li>{@code app.outbox.retention-batch-size} (default 1000) — rows per statement.</li>
 *   <li>{@code app.outbox.enabled=false} — removes the dispatcher and this job together.</li>
 * </ul>
 * The schedule itself is no longer a property: {@link #defaultCron()} seeds the
 * initial value and the DB row (editable in the console) is the source of truth after.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", matchIfMissing = true)
public class OutboxRetentionJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;
    private final int batchSize;

    public OutboxRetentionJob(JdbcTemplate jdbc,
                              @Value("${app.outbox.retention-days:30}") int retentionDays,
                              @Value("${app.outbox.retention-batch-size:1000}") int batchSize) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    @Override
    public String code() {
        return "core:outbox-retention";
    }

    /** Daily at 03:30. After the first sync the DB value wins (editable in the console). */
    @Override
    public String defaultCron() {
        return "0 30 3 * * *";
    }

    /** Housekeeping should run out of the box. */
    @Override
    public boolean enabledByDefault() {
        return true;
    }

    /** A purge can take a while on a large backlog — give the lock room. */
    @Override
    public int maxRunSeconds() {
        return 600;
    }

    @Override
    public void execute(JobContext ctx) {
        if (retentionDays <= 0) {
            return;   // retention disabled (code-level off switch)
        }
        long total = 0;
        int deleted;
        do {
            deleted = jdbc.update(
                    "DELETE FROM core_domain_event WHERE id IN ("
                            + "  SELECT id FROM core_domain_event "
                            + "   WHERE dispatch_state = 1 "
                            + "     AND dispatched_at < now() - make_interval(days => ?) "
                            + "   ORDER BY id LIMIT ?)",
                    retentionDays, batchSize);
            total += deleted;
        } while (deleted > 0);

        if (total > 0) {
            log.info("[outbox:retention] purged {} dispatched domain event(s) older than {} day(s)",
                    total, retentionDays);
        }
    }
}
