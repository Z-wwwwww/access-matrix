package com.platform.system.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.event.entity.DomainEventEntity;
import com.platform.core.infrastructure.event.mapper.DomainEventMapper;
import com.platform.system.platform.dto.EventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read + redrive over the {@code core_domain_event} outbox for the platform
 * console (gated by {@code platform:event:*}). Callers are platform-ops users
 * whose JWT carries {@code tid='system'}, so the MyBatis-Plus tenant interceptor
 * is bypassed and these queries/updates span all tenants — the same cross-tenant
 * model the {@code OutboxDispatcher} and tenant/job admin use.
 *
 * <p><b>Redrive</b> only touches FAILED rows ({@code dispatch_state=2}): it resets
 * them to pending ({@code 0}) and zeroes {@code dispatch_attempts} so the
 * dispatcher picks them up again with a fresh retry budget. Pending rows are
 * already queued and dispatched rows must not be re-sent (the downstream sink is
 * only required to be idempotent on re-delivery, not on deliberate replay).
 */
@Service
public class EventAdminService {

    private static final Logger log = LoggerFactory.getLogger(EventAdminService.class);

    private static final int STATE_PENDING = 0;
    private static final int STATE_FAILED  = 2;

    private final DomainEventMapper mapper;

    public EventAdminService(DomainEventMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<EventDto.View> list(long page, long size, Integer dispatchState,
                                          String eventType, String aggregateType, String keyword) {
        Page<DomainEventEntity> p = new Page<>(page, size);
        QueryWrapper<DomainEventEntity> w = new QueryWrapper<DomainEventEntity>()
                // Skip the JSONB payload in the list query — it's only needed in the detail view.
                .select("id", "tenant_id", "aggregate_type", "aggregate_id", "event_type",
                        "actor", "actor_type", "trace_id", "occurred_at",
                        "dispatch_state", "dispatch_attempts", "dispatched_at")
                .orderByDesc("occurred_at");
        if (dispatchState != null) {
            w.eq("dispatch_state", dispatchState);
        }
        if (eventType != null && !eventType.isBlank()) {
            w.eq("event_type", eventType);
        }
        if (aggregateType != null && !aggregateType.isBlank()) {
            w.eq("aggregate_type", aggregateType);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("aggregate_id", keyword)
                    .or().like("event_type", keyword)
                    .or().like("trace_id", keyword));
        }
        Page<DomainEventEntity> result = mapper.selectPage(p, w);
        List<EventDto.View> records = result.getRecords().stream().map(EventAdminService::toView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public EventDto.Detail get(String id) {
        DomainEventEntity e = mapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Domain event not found: " + id);
        }
        return new EventDto.Detail(
                e.getId(), e.getTenantId(), e.getAggregateType(), e.getAggregateId(),
                e.getEventType(), e.getPayload(), e.getActor(), e.getActorType(), e.getTraceId(),
                e.getOccurredAt(), e.getDispatchState(), e.getDispatchAttempts(), e.getDispatchedAt());
    }

    /** Reset a single FAILED event back to pending so the dispatcher retries it. */
    @Transactional
    public void redrive(String id) {
        DomainEventEntity e = mapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Domain event not found: " + id);
        }
        if (!Integer.valueOf(STATE_FAILED).equals(e.getDispatchState())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Only failed events (dispatch_state=2) can be redriven");
        }
        resetToPending(new UpdateWrapper<DomainEventEntity>().eq("id", id).eq("dispatch_state", STATE_FAILED));
        log.info("[event] redrive requested for {} (was failed → pending)", id);
    }

    /** Bulk: reset ALL failed events to pending. Returns the number reset. */
    @Transactional
    public int redriveAllFailed() {
        int n = resetToPending(new UpdateWrapper<DomainEventEntity>().eq("dispatch_state", STATE_FAILED));
        log.info("[event] bulk redrive: {} failed event(s) reset to pending", n);
        return n;
    }

    private int resetToPending(UpdateWrapper<DomainEventEntity> where) {
        return mapper.update(null, where
                .set("dispatch_state", STATE_PENDING)
                .set("dispatch_attempts", 0));
    }

    private static EventDto.View toView(DomainEventEntity e) {
        return new EventDto.View(
                e.getId(), e.getTenantId(), e.getAggregateType(), e.getAggregateId(),
                e.getEventType(), e.getActor(), e.getActorType(), e.getTraceId(),
                e.getOccurredAt(), e.getDispatchState(), e.getDispatchAttempts(), e.getDispatchedAt());
    }
}
