package com.platform.system.notification.sse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
                    // Route through remove() so a key whose last emitter just
                    // died is unmapped too — a bare list.remove leaves an empty
                    // list stranded in `conns` forever.
                    remove(key, em);
                }
            }
        });
    }

    private String key(String tenant, String user) {
        return tenant + ":" + user;
    }

    public SseEmitter register(String tenant, String user) {
        return register(tenant, user, new SseEmitter(TIMEOUT_MS));
    }

    /**
     * Register a caller-supplied emitter. Package-private seam so tests can
     * observe delivery; production always goes through {@link #register(String, String)}.
     *
     * <p>The add happens <b>inside</b> {@code compute} on purpose. With
     * {@code computeIfAbsent(...)} followed by a bare {@code list.add(...)}, the
     * add lands outside the map's bin lock, so {@link #remove} could empty and
     * unmap the very list this call just obtained — leaving a live connection in
     * a list no longer reachable from {@code conns}. That emitter then matched no
     * {@link #sendUnread} lookup and the user's unread badge silently froze until
     * the 30-minute timeout forced a reconnect. Doing the add under the same lock
     * {@code remove}'s {@code computeIfPresent} takes makes the two mutually
     * exclusive.
     *
     * <p>Callbacks are wired before the add so a connection that dies during
     * registration still removes itself.
     */
    SseEmitter register(String tenant, String user, SseEmitter emitter) {
        String k = key(tenant, user);
        emitter.onCompletion(() -> remove(k, emitter));
        emitter.onTimeout(() -> remove(k, emitter));
        emitter.onError(e -> remove(k, emitter));
        conns.compute(k, (ignored, list) -> {
            CopyOnWriteArrayList<SseEmitter> l = (list == null) ? new CopyOnWriteArrayList<>() : list;
            l.add(emitter);
            return l;
        });
        return emitter;
    }

    /**
     * Tear down one connection — what the emitter's completion / timeout / error
     * callbacks do. Package-private seam so tests can drive teardown without a
     * servlet container to fire those callbacks.
     */
    void unregister(String tenant, String user, SseEmitter emitter) {
        remove(key(tenant, user), emitter);
    }

    /** Open connections for one user. Package-private — test observability only. */
    int connectionCount(String tenant, String user) {
        CopyOnWriteArrayList<SseEmitter> list = conns.get(key(tenant, user));
        return list == null ? 0 : list.size();
    }

    /** Keys currently held. Package-private — test observability only. */
    int trackedKeys() {
        return conns.size();
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
                remove(key(tenant, user), em);
                log.debug("[notif-sse] dropped dead emitter for {}: {}", key(tenant, user), e.toString());
            }
        }
    }

    /**
     * Drop one emitter, and the whole key once it holds none.
     *
     * <p>Must go through {@code computeIfPresent} rather than
     * {@code get} + {@code conns.remove(key)}: the latter raced with
     * {@link #register}. Interleaving was
     * {@code register.computeIfAbsent} → (this method empties the list and
     * unmaps the key) → {@code list.add(newEmitter)}, which parked the new
     * connection in a list no longer reachable from {@code conns}. That
     * emitter then never matched a {@link #sendUnread} lookup, so the user's
     * unread badge silently stopped updating until the SSE timeout (30 min)
     * forced a reconnect — the exact "badge looks frozen" symptom the
     * heartbeat above exists to prevent. Concretely triggered by a browser
     * reconnect landing while the previous connection is being torn down,
     * which is the normal shape of a tab refresh.
     *
     * <p>{@code computeIfPresent} and {@code computeIfAbsent} lock the same
     * bin of the {@link ConcurrentHashMap}, so the unmap and the add can no
     * longer interleave.
     */
    private void remove(String key, SseEmitter emitter) {
        conns.computeIfPresent(key, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;   // null → unmap the key atomically
        });
    }
}
