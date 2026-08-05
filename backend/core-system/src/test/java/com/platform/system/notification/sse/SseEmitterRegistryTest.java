package com.platform.system.notification.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the registry's book-keeping invariant: <b>every emitter handed out by
 * {@link SseEmitterRegistry#register} stays reachable from
 * {@link SseEmitterRegistry#sendUnread} until it is torn down</b>, and a key whose
 * last emitter is gone does not linger.
 *
 * <p>Both used to break. Removal was {@code conns.get(key)} → mutate the list →
 * {@code conns.remove(key)} when empty, and registration was
 * {@code computeIfAbsent} → {@code list.add(...)} with the add <em>outside</em> the
 * map's bin lock. The interleaving {@code computeIfAbsent} → (remove empties and
 * unmaps the key) → {@code list.add(newEmitter)} parks a live connection in a list
 * no longer reachable from {@code conns}. That user's unread badge then silently
 * stops updating until the 30-minute SSE timeout forces a reconnect — and a tab
 * refresh, where a new connection lands while the old one tears down, is exactly
 * that interleaving. The drop-on-send-failure paths had the same shape and
 * additionally stranded an empty list under the key forever.
 */
class SseEmitterRegistryTest {

    /** A dead client: every send throws, like a closed connection. */
    private static SseEmitter deadOnSend() {
        return new SseEmitter() {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IOException("client gone");
            }
        };
    }

    /** Counts successful pushes so we can tell "delivered" from "silently dropped". */
    private static final class CountingEmitter extends SseEmitter {
        final AtomicInteger sent = new AtomicInteger();

        @Override
        public void send(SseEventBuilder builder) {
            sent.incrementAndGet();
        }
    }

    @Test
    void dropping_a_dead_emitter_also_unmaps_its_now_empty_key() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        registry.register("acme", "U1", deadOnSend());
        assertThat(registry.trackedKeys()).isEqualTo(1);

        registry.sendUnread("acme", "U1", 7);

        assertThat(registry.connectionCount("acme", "U1")).isZero();
        // The key must go too — a bare list.remove left an empty list stranded
        // under the key for the life of the JVM.
        assertThat(registry.trackedKeys()).isZero();
    }

    @Test
    void an_emitter_registered_after_a_full_teardown_still_receives_pushes() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        SseEmitter first = registry.register("acme", "U1");
        registry.unregister("acme", "U1", first);
        assertThat(registry.trackedKeys()).isZero();

        CountingEmitter second = new CountingEmitter();
        registry.register("acme", "U1", second);
        registry.sendUnread("acme", "U1", 3);

        assertThat(second.sent.get()).isEqualTo(1);
    }

    @Test
    void every_open_connection_of_a_user_gets_the_push() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        CountingEmitter tabA = new CountingEmitter();
        CountingEmitter tabB = new CountingEmitter();
        registry.register("acme", "U1", tabA);
        registry.register("acme", "U1", tabB);

        registry.sendUnread("acme", "U1", 5);

        assertThat(tabA.sent.get()).isEqualTo(1);
        assertThat(tabB.sent.get()).isEqualTo(1);
        assertThat(registry.connectionCount("acme", "U1")).isEqualTo(2);
    }

    @Test
    void a_push_never_crosses_users() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        CountingEmitter mine = new CountingEmitter();
        CountingEmitter theirs = new CountingEmitter();
        registry.register("acme", "U1", mine);
        registry.register("acme", "U2", theirs);

        registry.sendUnread("acme", "U1", 1);

        assertThat(mine.sent.get()).isEqualTo(1);
        assertThat(theirs.sent.get()).isZero();
    }

    @Test
    void concurrent_register_and_teardown_never_orphans_a_connection() throws Exception {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        int rounds = 20_000;
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger orphaned = new AtomicInteger();

        // Churner: register + tear down for the SAME key, over and over. This is
        // what drives the list to empty (and the key to unmap) concurrently with
        // the reconnector's registration — the exact window the bug lived in.
        Thread churn = new Thread(() -> {
            for (int i = 0; i < rounds; i++) {
                SseEmitter e = registry.register("acme", "U1");
                registry.unregister("acme", "U1", e);
            }
            done.countDown();
        }, "sse-churn");

        // Reconnector: register, push, and flag a push that never arrived —
        // the observable symptom of an emitter stranded in an unmapped list.
        Thread reconnect = new Thread(() -> {
            for (int i = 0; i < rounds; i++) {
                CountingEmitter e = new CountingEmitter();
                registry.register("acme", "U1", e);
                registry.sendUnread("acme", "U1", i);
                if (e.sent.get() == 0) orphaned.incrementAndGet();
                registry.unregister("acme", "U1", e);
            }
            done.countDown();
        }, "sse-reconnect");

        churn.start();
        reconnect.start();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

        assertThat(orphaned.get())
                .as("a registered emitter was unreachable from sendUnread")
                .isZero();
        // Nothing outstanding → no key may survive.
        assertThat(registry.trackedKeys()).isZero();
    }
}
