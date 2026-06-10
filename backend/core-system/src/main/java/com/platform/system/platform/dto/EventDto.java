package com.platform.system.platform.dto;

import java.time.OffsetDateTime;

/**
 * DTO container for the platform domain-event console (read + redrive over
 * {@code core_domain_event}). Two shapes: a light {@link View} for the list
 * (no payload) and a full {@link Detail} that carries the JSONB payload, fetched
 * only when an operator opens a single event.
 */
public final class EventDto {

    private EventDto() {}

    /** List row — deliberately omits the (potentially large) JSONB payload. */
    public record View(
            String id,
            String tenantId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String actor,
            Integer actorType,
            String traceId,
            OffsetDateTime occurredAt,
            Integer dispatchState,      // 0 pending / 1 dispatched / 2 failed
            Integer dispatchAttempts,
            OffsetDateTime dispatchedAt
    ) {}

    /** Full event including the JSONB payload — returned by GET /platform/events/{id}. */
    public record Detail(
            String id,
            String tenantId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            String actor,
            Integer actorType,
            String traceId,
            OffsetDateTime occurredAt,
            Integer dispatchState,
            Integer dispatchAttempts,
            OffsetDateTime dispatchedAt
    ) {}
}
