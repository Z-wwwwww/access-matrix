package com.platform.core.infrastructure.event;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.infrastructure.event.entity.DomainEventEntity;
import com.platform.core.infrastructure.event.mapper.DomainEventMapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

/**
 * Default {@link EventPublisher}: synchronously inserts the event into
 * {@code core_domain_event}, joining the caller's transaction (no
 * {@code @Async}, no {@code REQUIRES_NEW}) so the event and the business
 * write commit or roll back together — the transactional-outbox guarantee.
 *
 * <p>Tenant / actor / trace are read from {@link RequestContext} at publish
 * time. This works because the call runs on the request thread inside the
 * business transaction (the ThreadLocal is still populated), unlike the async
 * audit sink which must carry tenant in its record.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private final DomainEventMapper mapper;
    private final JsonMapper jsonMapper;

    public OutboxEventPublisher(DomainEventMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        RequestContext ctx = RequestContext.current();

        DomainEventEntity e = new DomainEventEntity();
        e.setId(IdGenerator.ulid());
        e.setTenantId(RequestContext.tenantIdOrDefault());
        e.setAggregateType(event.aggregateType());
        e.setAggregateId(event.aggregateId());
        e.setEventType(event.eventType());
        // Jackson 3 throws unchecked on failure; we let it propagate so a bad
        // payload rolls back the business write rather than silently dropping the event.
        e.setPayload(event.payload() == null ? null : jsonMapper.writeValueAsString(event.payload()));
        e.setActor(RequestContext.userId());
        e.setActorType(event.actorType().code());
        e.setTraceId(ctx == null ? null : ctx.getTraceId());
        e.setOccurredAt(OffsetDateTime.now());
        e.setDispatchState(0);
        e.setDispatchAttempts(0);

        mapper.insert(e);
    }
}
