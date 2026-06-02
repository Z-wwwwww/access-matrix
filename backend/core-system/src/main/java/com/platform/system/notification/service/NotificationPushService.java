package com.platform.system.notification.service;

import com.platform.system.notification.sse.SseEmitterRegistry;
import org.springframework.stereotype.Service;

/**
 * Pushes the recipient's current unread count to their open SSE connections.
 * Recomputes from the DB (the source of truth) so the badge is always exact,
 * even after a missed/dropped push or a reconnect.
 *
 * <p>Single-instance for now (delegates straight to the in-JVM
 * {@link SseEmitterRegistry}). See that class for the Redis pub/sub upgrade
 * needed when running multiple instances.
 */
@Service
public class NotificationPushService {

    private final NotificationService notificationService;
    private final SseEmitterRegistry registry;

    public NotificationPushService(NotificationService notificationService, SseEmitterRegistry registry) {
        this.notificationService = notificationService;
        this.registry = registry;
    }

    public void notifyUser(String tenantId, String userId) {
        long unread = notificationService.unreadCount(tenantId, userId);
        registry.sendUnread(tenantId, userId, unread);
    }
}
