package com.platform.core.infrastructure.event;

/**
 * Records a {@link DomainEvent} into the platform event store / outbox
 * ({@code core_domain_event}).
 *
 * <p><b>Transactional outbox contract.</b> Unlike audit logging
 * ({@code OpLogSink}, which is async/best-effort), this write MUST happen
 * inside the same database transaction as the business state change it
 * describes. Call it from within the business {@code @Transactional} service
 * method, after the entity write:
 *
 * <pre>{@code
 * @Transactional
 * public void changePrice(...) {
 *     rateMapper.updateById(rate);                 // the business write
 *     events.publish(DomainEvent.of("Rate", rate.getId(),
 *                                   "rate.price_changed", payloadDto));  // same tx
 * }
 * }</pre>
 *
 * If the event insert fails, the business transaction rolls back — by design
 * there is no business write without its event, and no event without the
 * write. Exceptions are therefore <b>not</b> swallowed.
 *
 * <p>The interface is what business modules depend on; the outbox dispatcher
 * that later streams these rows to analytics / a message bus is a separate
 * concern and does not change this contract.
 */
public interface EventPublisher {

    void publish(DomainEvent event);
}
