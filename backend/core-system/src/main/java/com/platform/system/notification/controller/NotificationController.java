package com.platform.system.notification.controller;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.system.notification.dto.NotificationDto;
import com.platform.system.notification.service.NotificationService;
import com.platform.system.notification.sse.SseEmitterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;

/**
 * In-app notification endpoints. All "read" operations are self-scoped to the
 * caller (recipient_user_id = current user) and need only authentication — no
 * {@code @RequiresPermission}, same as {@code /menu/me}.
 *
 * <p>The SSE stream can't carry a Bearer header (EventSource limitation), so
 * it authenticates with a short-lived one-time ticket minted by an
 * authenticated POST. {@code /notification/stream} is added to
 * {@code SecurityConfig.PERMIT_PATHS}; every other path here stays behind JWT.
 */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private static final String TICKET_PREFIX = "notif:sse-ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final NotificationService service;
    private final SseEmitterRegistry registry;
    private final StringRedisTemplate redis;

    public NotificationController(NotificationService service,
                                  SseEmitterRegistry registry,
                                  StringRedisTemplate redis) {
        this.service = service;
        this.registry = registry;
        this.redis = redis;
    }

    /** Mint a one-time SSE ticket (authenticated). Frontend then opens the stream with it. */
    @PostMapping("/sse-ticket")
    public JsonResult<String> sseTicket() {
        String userId = currentUserOr401();
        String ticket = IdGenerator.ulid();
        redis.opsForValue().set(TICKET_PREFIX + ticket,
                RequestContext.tenantIdOrDefault() + ":" + userId, TICKET_TTL);
        return JsonResult.ok(ticket);
    }

    /** Long-lived SSE connection. Auth via one-time ticket (consumed on connect). */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("ticket") String ticket) {
        // Consume the ticket with an atomic GETDEL (one round trip) so two
        // concurrent requests carrying the SAME ticket cannot both observe a
        // non-empty value — at most one wins, exactly like
        // RefreshTokenStore.rotate. The previous GET-then-DELETE pair left a
        // window where a replayed ticket opened a second authenticated stream;
        // that matters here because the ticket travels in the QUERY STRING (an
        // EventSource can't send headers), so it lands in proxy/access logs and
        // browser history. GETDEL needs Redis 6.2+, which the documented
        // requirement already exceeds (docs/getting-started.md: Redis 7+).
        String key = TICKET_PREFIX + ticket;
        String v = redis.opsForValue().getAndDelete(key);

        if (v == null) {
            // Invalid / expired / replayed ticket. Don't throw — the
            // @ExceptionHandler would try to render JSON, but the request's
            // Accept is text/event-stream (→ 406). Just close the stream; the
            // client mints a fresh ticket and reconnects.
            SseEmitter dead = new SseEmitter();
            dead.complete();
            return dead;
        }
        int sep = v.indexOf(':');
        String tenant = v.substring(0, sep);
        String user = v.substring(sep + 1);

        SseEmitter emitter = registry.register(tenant, user);
        try {
            // First frame = current unread count, recomputed from the DB, so a
            // freshly (re)connected client immediately syncs an exact badge.
            emitter.send(SseEmitter.event().name("unread").data(service.unreadCount(tenant, user)));
        } catch (IOException e) {
            log.debug("[notif-sse] initial send failed for {}:{} : {}", tenant, user, e.toString());
        }
        return emitter;
    }

    @GetMapping("/unread-count")
    public JsonResult<Long> unreadCount() {
        return JsonResult.ok(service.unreadCount(RequestContext.tenantIdOrDefault(), currentUserOr401()));
    }

    @GetMapping("/list")
    public JsonResult<PageResult<NotificationDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer readFlag) {
        return JsonResult.ok(service.list(currentUserOr401(), page, size, readFlag));
    }

    @PostMapping("/{id}/read")
    public JsonResult<Void> read(@PathVariable String id) {
        service.markRead(id, currentUserOr401());
        return JsonResult.ok();
    }

    @PostMapping("/read-all")
    public JsonResult<Void> readAll() {
        service.markAllRead(currentUserOr401());
        return JsonResult.ok();
    }

    private String currentUserOr401() {
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return userId;
    }
}
