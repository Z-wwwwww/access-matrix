package com.platform.system.notification.sse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Per-JVM registry of open SSE connections, keyed by {@code tenant:user}.
 * A user may have several (multiple tabs) — all get the push.
 *
 * <p>SINGLE-INSTANCE delivery. When you scale to >1 app instance, a user's
 * connection lives on whichever instance it hit, so a push originating on
 * another instance won't find it here. The fix is ~10 lines: publish
 * {@code tenant:user} to a Redis channel on notify, and have each instance
 * subscribe and call {@link #sendUnread} for keys it holds. Left out for now
 * to keep the first cut dependency-light.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    /** 30 minutes — the browser reconnects (with a fresh ticket) on timeout. */
    private static final long TIMEOUT_MS = 30 * 60_000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> conns = new ConcurrentHashMap<>();

    /**
     * Heartbeat. Browsers and (dev) proxies silently drop an SSE connection
     * that's been idle for ~60s — after which pushes never arrive and the
     * badge looks frozen until a manual refresh. A periodic comment frame
     * keeps the connection alive and forces a flush through any buffering
     * proxy. Comment frames ({@code :ping}) are ignored by EventSource, so
     * they never reach the client's "unread" handler.
     */
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "notif-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void startHeartbeat() {
        heartbeat.scheduleAtFixedRate(this::pingAll, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeat.shutdownNow();
    }

    private void pingAll() {
        conns.forEach((key, list) -> {
            for (SseEmitter em : list) {
                try {
                    em.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    list.remove(em);
                }
            }
        });
    }

    private String key(String tenant, String user) {
        return tenant + ":" + user;
    }

    public SseEmitter register(String tenant, String user) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        String k = key(tenant, user);
        CopyOnWriteArrayList<SseEmitter> list = conns.computeIfAbsent(k, x -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(k, emitter));
        emitter.onTimeout(() -> remove(k, emitter));
        emitter.onError(e -> remove(k, emitter));
        return emitter;
    }

    /** Push the current unread count to every open connection of this user. */
    public void sendUnread(String tenant, String user, long unread) {
        CopyOnWriteArrayList<SseEmitter> list = conns.get(key(tenant, user));
        if (list == null || list.isEmpty()) return;
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name("unread").data(unread));
            } catch (IOException | IllegalStateException e) {
                // Client gone / already closed — drop it. completion/error
                // callbacks usually already removed it; this is belt-and-braces.
                list.remove(em);
                log.debug("[notif-sse] dropped dead emitter for {}: {}", key(tenant, user), e.toString());
            }
        }
    }

    private void remove(String key, SseEmitter emitter) {
        List<SseEmitter> list = conns.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) conns.remove(key);
        }
    }
}
