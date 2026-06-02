package com.platform.core.common.notification;

/**
 * Published by a business module once an action-required notification's
 * underlying decision is done (e.g. a price-adjustment plan was confirmed or
 * rejected, a task was completed). The notification core marks every still-
 * unread notification pointing at this {@code bizType + bizId} as read, and
 * pushes the updated unread count to those recipients over SSE.
 *
 * <p>Keeps the "what does 'handled' mean" logic in the business module — the
 * notification core only reacts to the fact that it happened.
 *
 * @param tenantId tenant the record (and its notifications) live in
 * @param bizType  source entity type, e.g. {@code "demo_task"}
 * @param bizId    source entity id (ULID)
 */
public record NotificationResolvedEvent(String tenantId, String bizType, String bizId) {
}
