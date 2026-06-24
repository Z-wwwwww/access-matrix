package com.platform.core.infrastructure.event;

import java.util.Objects;

/**
 * An immutable domain fact to be recorded via {@link EventPublisher} — "what
 * changed in the business", as opposed to {@code core_oplog} which records
 * "who called which endpoint".
 *
 * <p>The caller supplies only the business-meaningful fields. Tenant, actor
 * id, trace id and timestamp are filled by the publisher from
 * {@link com.platform.core.common.context.RequestContext} at publish time.
 *
 * @param aggregateType the kind of entity the event is about, e.g. {@code "Reservation"} / {@code "Rate"}
 * @param aggregateId   the id of that entity
 * @param eventType     a dotted verb-phrase, e.g. {@code "reservation.created"} / {@code "rate.price_changed"}
 * @param payload       a serializable object describing the fact; serialized to JSONB by the publisher (may be null)
 * @param actorType     who caused it; defaults to {@link ActorType#HUMAN} when null
 */
public record DomainEvent(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payload,
        ActorType actorType) {

    public DomainEvent {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        if (actorType == null) {
            actorType = ActorType.HUMAN;
        }
    }

    /** A change driven by the current authenticated user — the common case. */
    public static DomainEvent of(String aggregateType, String aggregateId, String eventType, Object payload) {
        return new DomainEvent(aggregateType, aggregateId, eventType, payload, ActorType.HUMAN);
    }

    /** A change driven by an AI service account (recommendation accepted / autopilot). */
    public static DomainEvent ai(String aggregateType, String aggregateId, String eventType, Object payload) {
        return new DomainEvent(aggregateType, aggregateId, eventType, payload, ActorType.AI);
    }

    /** A change driven by an automated/background process (no human actor). */
    public static DomainEvent system(String aggregateType, String aggregateId, String eventType, Object payload) {
        return new DomainEvent(aggregateType, aggregateId, eventType, payload, ActorType.SYSTEM);
    }
}
