package com.platform.core.infrastructure.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.event.entity.DomainEventEntity;
import com.platform.core.infrastructure.event.mapper.DomainEventMapper;
import com.platform.core.infrastructure.scheduling.JobLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Drains the {@code core_domain_event} outbox: every poll it fetches the
 * oldest pending rows ({@code dispatch_state = 0}, ordered by ULID id) and
 * hands each to the {@link EventDispatchSink}, marking it dispatched (1) on
 * success or, after {@code app.outbox.max-attempts} failures, failed (2).
 *
 * <p><b>Cross-tenant scan.</b> A poll is platform-ops, not a user request, so
 * it sets {@link RequestContext} to the {@code "system"} tenant for its
 * duration — that is exactly the signal {@code MybatisPlusConfig}'s
 * {@code ignoreTable} uses to bypass tenant scoping for every table, letting
 * the plain BaseMapper queries below read/update across all tenants without
 * any {@code @InterceptorIgnore} or hand-written SQL.
 *
 * <p>Each row is committed in its own statement (no batch transaction): the
 * outbox is at-least-once, so a crash mid-batch simply reprocesses whatever
 * is still pending.
 *
 * <p><b>One pod at a time.</b> The poll holds the {@code core_job_lock} row
 * {@value #LOCK_NAME} for its duration. Without it, the production topology in
 * {@code docs/deployment.md} (「Backend pod×2~N … 後端無状態 → 水平扩」) makes every
 * pod select the same {@code dispatch_state = 0} rows in the same window and
 * dispatch all of them: the row is only written back <em>after</em> the sink
 * returns, so nothing else stops a second pod from picking it up. That would turn
 * the at-least-once contract {@link EventDispatchSink} documents — a duplicate
 * means a crash landed between "downstream accepted" and "row marked dispatched"
 * — into N-fold delivery of every event on the happy path. This is the same
 * {@code JobLockService} every {@link com.platform.core.common.scheduling.ScheduledJob}
 * gets via {@code JobExecutionWrapper}; the dispatcher stays a plain
 * {@code @Scheduled} only because its cadence is finer than the cron job model,
 * which was never a reason to run it on every pod at once.
 *
 * <p>The lease ({@code app.outbox.lock-lease-seconds}, default 60) bounds how long
 * a pod that dies mid-batch can keep the lock: after it expires another pod takes
 * over and re-processes whatever is still pending — at-least-once, unchanged.
 * Acquisition failure is not an error; it just means another pod is draining.
 *
 * <p>Disable with {@code app.outbox.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    /** Must match {@code MybatisPlusConfig.PLATFORM_TENANT_ID} — the scoping-bypass signal. */
    private static final String PLATFORM_TENANT = "system";

    /** {@code core_job_lock} row name. Shape matches JobExecutionWrapper's "{code}::{tenant}". */
    static final String LOCK_NAME = "outbox-dispatcher::system";

    private final DomainEventMapper mapper;
    private final EventDispatchSink sink;
    private final JobLockService lockService;
    private final int batchSize;
    private final int maxAttempts;
    private final int lockLeaseSeconds;

    public OutboxDispatcher(DomainEventMapper mapper,
                            ObjectProvider<EventDispatchSink> sinkProvider,
                            JobLockService lockService,
                            @Value("${app.outbox.batch-size:200}") int batchSize,
                            @Value("${app.outbox.max-attempts:5}") int maxAttempts,
                            @Value("${app.outbox.lock-lease-seconds:60}") int lockLeaseSeconds) {
        this.mapper = mapper;
        this.sink = sinkProvider.getIfAvailable(LoggingEventDispatchSink::new);
        this.lockService = lockService;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.lockLeaseSeconds = lockLeaseSeconds;
        if (this.sink instanceof LoggingEventDispatchSink) {
            log.info("[OutboxDispatcher] no EventDispatchSink bean configured — using logging fallback; "
                    + "domain events are persisted but not forwarded downstream until a real sink is registered.");
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void dispatchPending() {
        if (!lockService.tryAcquire(LOCK_NAME, lockLeaseSeconds)) {
            // Another pod is draining this batch. Not a failure — the next tick
            // on whichever pod wins the lock picks up whatever is still pending.
            return;
        }
        RequestContext.set(PLATFORM_TENANT, null, "outbox-dispatcher", null, null);
        try {
            Page<DomainEventEntity> page = Page.of(1, batchSize);
            page.setSearchCount(false);
            LambdaQueryWrapper<DomainEventEntity> w = new LambdaQueryWrapper<DomainEventEntity>()
                    .eq(DomainEventEntity::getDispatchState, 0)
                    .orderByAsc(DomainEventEntity::getId);

            List<DomainEventEntity> batch = mapper.selectPage(page, w).getRecords();
            if (batch.isEmpty()) {
                return;
            }

            int ok = 0, failed = 0;
            for (DomainEventEntity e : batch) {
                try {
                    sink.dispatch(e);
                    markDispatched(e);
                    ok++;
                } catch (Exception ex) {
                    markFailed(e, ex);
                    failed++;
                }
            }
            log.debug("[OutboxDispatcher] dispatched={} failed={} (batch={})", ok, failed, batch.size());
        } catch (Exception ex) {
            // Never let a poll failure kill the scheduler; next tick retries.
            log.warn("[OutboxDispatcher] poll failed: {}", ex.getMessage());
        } finally {
            // Release before clearing the context: both must happen even if the
            // batch above threw, or the lease would pin the lock for its full
            // duration and stall every pod, not just this one.
            lockService.release(LOCK_NAME);
            RequestContext.clear();
        }
    }

    private void markDispatched(DomainEventEntity e) {
        DomainEventEntity patch = new DomainEventEntity();
        patch.setId(e.getId());
        patch.setDispatchState(1);
        patch.setDispatchAttempts(attempts(e) + 1);
        patch.setDispatchedAt(OffsetDateTime.now());
        mapper.updateById(patch);
    }

    private void markFailed(DomainEventEntity e, Exception ex) {
        int attempts = attempts(e) + 1;
        DomainEventEntity patch = new DomainEventEntity();
        patch.setId(e.getId());
        patch.setDispatchAttempts(attempts);
        patch.setDispatchState(attempts >= maxAttempts ? 2 : 0);  // give up after maxAttempts, else retry next poll
        mapper.updateById(patch);
        log.warn("[OutboxDispatcher] event {} dispatch failed (attempt {}/{}): {}",
                e.getId(), attempts, maxAttempts, ex.getMessage());
    }

    private static int attempts(DomainEventEntity e) {
        return e.getDispatchAttempts() == null ? 0 : e.getDispatchAttempts();
    }
}
