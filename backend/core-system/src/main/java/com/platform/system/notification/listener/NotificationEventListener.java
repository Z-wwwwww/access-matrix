package com.platform.system.notification.listener;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.notification.NotificationEvent;
import com.platform.core.common.notification.NotificationResolvedEvent;
import com.platform.system.notification.service.NotificationPushService;
import com.platform.system.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a {@link NotificationEvent} into a persisted row + an instant SSE push.
 *
 * <p>{@code AFTER_COMMIT} so a rolled-back business transaction never emits a
 * phantom notification. {@code @Async} so it never blocks the business
 * response — same posture as {@code OpLogService} / {@code LoginAuditService}.
 *
 * <p>Because the async thread has no {@code RequestContext}, we re-establish
 * it from the event's tenant before touching the DB so both the tenant
 * interceptor and the audit-field filler behave exactly as on a web thread.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final NotificationPushService pushService;

    public NotificationEventListener(NotificationService notificationService,
                                     NotificationPushService pushService) {
        this.notificationService = notificationService;
        this.pushService = pushService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NotificationEvent e) {
        if (e.recipientUserId() == null || e.recipientUserId().isBlank()) return;
        RequestContext.set(e.tenantId(), "system", "system", null, null);
        try {
            notificationService.create(e);                       // ① durable row
            pushService.notifyUser(e.tenantId(), e.recipientUserId()); // ② instant red dot
        } catch (Exception ex) {
            log.error("[notif] failed to deliver type={} to user={} tenant={}: {}",
                    e.type(), e.recipientUserId(), e.tenantId(), ex.toString(), ex);
        } finally {
            RequestContext.clear();
        }
    }

    /**
     * A business decision behind an action notification finished → mark every
     * still-unread notification for that record as read, and push the updated
     * unread count to each affected recipient.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResolved(NotificationResolvedEvent e) {
        if (e.bizType() == null || e.bizId() == null) return;
        RequestContext.set(e.tenantId(), "system", "system", null, null);
        try {
            for (String userId : notificationService.markReadByBiz(e.tenantId(), e.bizType(), e.bizId())) {
                pushService.notifyUser(e.tenantId(), userId);
            }
        } catch (Exception ex) {
            log.error("[notif] failed to resolve bizType={} bizId={} tenant={}: {}",
                    e.bizType(), e.bizId(), e.tenantId(), ex.toString(), ex);
        } finally {
            RequestContext.clear();
        }
    }
}
