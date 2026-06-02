package com.platform.core.common.notification;

/**
 * In-app notification trigger. Any module publishes this via Spring's
 * {@code ApplicationEventPublisher}; the core notification module listens
 * (after commit, async) to persist a {@code core_notification} row and push
 * an instant unread-count update over SSE to the recipient.
 *
 * <p>Business modules depend ONLY on this record (it lives in core-common) —
 * they never import the notification entity / service. Adding a new reminder
 * type means publishing a new event with a new {@link #type}; the notification
 * core needs no change.
 *
 * <p>{@code tenantId} is carried explicitly because the listener runs on an
 * async worker thread that has no {@code RequestContext} populated — the
 * publishing thread's tenant must travel with the event.
 *
 * <p>{@code bizType} + {@code bizId} are the optional "open this entity's
 * detail" hint: when both are set, the frontend navigates to {@link #link}
 * (kept a clean route) and then opens that entity's drawer — the id travels
 * via a store, never the URL. When they're null, the click is a plain
 * navigation (open the page / a new tab).
 *
 * @param tenantId        recipient's tenant (NOT the platform 'system' tenant)
 * @param recipientUserId business user id (ULID) the notification is for
 * @param type            machine type, e.g. {@code "task.assigned"}
 * @param title           short headline shown in the bell dropdown
 * @param content         optional longer body (nullable)
 * @param link            frontend route to navigate to (a clean path, nullable)
 * @param bizType         source entity type for a drawer-open, e.g. {@code "demo_task"} (nullable)
 * @param bizId           source entity id (ULID) for a drawer-open (nullable)
 * @param kind            {@link #KIND_INFO} (FYI) or {@link #KIND_ACTION} (needs handling)
 */
public record NotificationEvent(
        String tenantId,
        String recipientUserId,
        String type,
        String title,
        String content,
        String link,
        String bizType,
        String bizId,
        int kind) {

    /** FYI — click to view. */
    public static final int KIND_INFO = 0;
    /** Needs the recipient to go handle something (confirm / reject / process). */
    public static final int KIND_ACTION = 1;

    /** Plain informational notification. */
    public static NotificationEvent info(String tenantId, String recipientUserId, String type,
            String title, String content, String link, String bizType, String bizId) {
        return new NotificationEvent(tenantId, recipientUserId, type, title, content, link,
                bizType, bizId, KIND_INFO);
    }

    /**
     * Action-required notification — the UI badges it differently and it stays
     * "to handle" until the business decision fires a
     * {@code NotificationResolvedEvent} (which marks it read).
     */
    public static NotificationEvent action(String tenantId, String recipientUserId, String type,
            String title, String content, String link, String bizType, String bizId) {
        return new NotificationEvent(tenantId, recipientUserId, type, title, content, link,
                bizType, bizId, KIND_ACTION);
    }
}
