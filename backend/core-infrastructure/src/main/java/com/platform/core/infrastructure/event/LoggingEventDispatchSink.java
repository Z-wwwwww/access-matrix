package com.platform.core.infrastructure.event;

import com.platform.core.infrastructure.event.entity.DomainEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback {@link EventDispatchSink} used when no real downstream sink is
 * registered. It only logs (at DEBUG, to stay quiet in production) and lets
 * the dispatcher mark the row dispatched.
 *
 * <p>This is deliberate foundation-stage behaviour: the durable record is the
 * {@code core_domain_event} row itself, which is never deleted by the
 * dispatcher. "Dispatched" here means "forwarded to (currently) nothing";
 * the time-series data still accumulates in the table from day one. Register
 * a real sink (Kafka / analytics writer) when a downstream consumer exists.
 *
 * <p>Not a {@code @Component} — {@link OutboxDispatcher} instantiates it as the
 * default only when {@code ObjectProvider<EventDispatchSink>} finds no bean.
 */
public class LoggingEventDispatchSink implements EventDispatchSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventDispatchSink.class);

    @Override
    public void dispatch(DomainEventEntity event) {
        log.debug("[domain-event] tenant={} type={} aggregate={}/{} actorType={} occurredAt={}",
                event.getTenantId(), event.getEventType(),
                event.getAggregateType(), event.getAggregateId(),
                event.getActorType(), event.getOccurredAt());
    }
}
