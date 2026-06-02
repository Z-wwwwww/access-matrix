package com.platform.core.infrastructure.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.event.entity.DomainEventEntity;
import com.platform.core.infrastructure.event.mapper.DomainEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
 * <p>Disable with {@code app.outbox.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    /** Must match {@code MybatisPlusConfig.PLATFORM_TENANT_ID} — the scoping-bypass signal. */
    private static final String PLATFORM_TENANT = "system";

    private final DomainEventMapper mapper;
    private final EventDispatchSink sink;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxDispatcher(DomainEventMapper mapper,
                            ObjectProvider<EventDispatchSink> sinkProvider,
                            @Value("${app.outbox.batch-size:200}") int batchSize,
                            @Value("${app.outbox.max-attempts:5}") int maxAttempts) {
        this.mapper = mapper;
        this.sink = sinkProvider.getIfAvailable(LoggingEventDispatchSink::new);
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        if (this.sink instanceof LoggingEventDispatchSink) {
            log.info("[OutboxDispatcher] no EventDispatchSink bean configured — using logging fallback; "
                    + "domain events are persisted but not forwarded downstream until a real sink is registered.");
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void dispatchPending() {
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
            RequestContext.clear();
        }
    }

    private void markDispatched(DomainEventEntity e) {
        DomainEventEntity patch = new DomainEventEntity();
        patch.setId(e.getId());
        patch.setDispatchState(1);
        patch.setDispatchAttempts(attempts(e) + 1);
        patch.setDispatchedAt(LocalDateTime.now());
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
