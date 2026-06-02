package com.platform.core.infrastructure.event;

import com.platform.core.infrastructure.event.entity.DomainEventEntity;

/**
 * Forwards a persisted domain event downstream — to a message bus, an
 * analytics store, a projection, etc. This is the seam a real consumer plugs
 * into: register a single {@code @Component} implementing this interface and
 * {@link OutboxDispatcher} will use it instead of the logging fallback.
 *
 * <p>Implementations should be idempotent: the outbox guarantees at-least-once
 * delivery (a crash between "downstream accepted" and "row marked dispatched"
 * re-delivers the event on the next poll).
 *
 * <p>Throw to signal failure — the dispatcher increments the attempt counter
 * and retries on a later poll (until {@code app.outbox.max-attempts}).
 */
public interface EventDispatchSink {

    void dispatch(DomainEventEntity event);
}
