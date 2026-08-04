package com.platform.system.notification.controller;

import com.platform.system.notification.service.NotificationService;
import com.platform.system.notification.sse.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SSE ticket handshake — the one place in the app where a credential
 * is carried in a QUERY STRING (an {@code EventSource} can't send an
 * Authorization header), so {@code /notification/stream} sits in
 * {@code SecurityConfig.PERMIT_PATHS} and this ticket is the only thing
 * standing between an anonymous request and a user's notification stream.
 *
 * <p>Two invariants:
 * <ol>
 *   <li>The ticket is consumed <b>atomically</b> ({@code GETDEL}), so two
 *       concurrent requests replaying the same ticket can't both get a stream.
 *       A GET-then-DELETE pair leaves exactly that window — and a query-string
 *       credential is the kind that leaks into proxy logs / browser history,
 *       so replay is the realistic attack, not a theoretical one.</li>
 *   <li>An invalid / expired / already-consumed ticket yields a completed
 *       (dead) emitter rather than an exception — throwing would make the
 *       exception handler try to render JSON onto a {@code text/event-stream}
 *       response (406) instead of letting the client reconnect.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationControllerTest {

    @Mock NotificationService service;
    @Mock SseEmitterRegistry registry;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private NotificationController controller() {
        when(redis.opsForValue()).thenReturn(valueOps);
        return new NotificationController(service, registry, redis);
    }

    @Test
    void stream_consumesTicketAtomicallyAndNeverWithAPlainGet() {
        NotificationController c = controller();
        when(valueOps.getAndDelete("notif:sse-ticket:T1")).thenReturn("acme:USER-1");
        when(registry.register("acme", "USER-1")).thenReturn(new SseEmitter());
        when(service.unreadCount("acme", "USER-1")).thenReturn(3L);

        c.stream("T1");

        verify(valueOps).getAndDelete("notif:sse-ticket:T1");
        // A plain GET (+ separate DELETE) would reopen the replay window.
        verify(valueOps, never()).get(anyString());
        verify(redis, never()).delete(anyString());
        verify(registry).register("acme", "USER-1");
    }

    @Test
    void stream_registersTheTicketsOwnerNotTheRequestsApparentIdentity() {
        // The stream is anonymous (no JWT), so the ticket's stored
        // "tenant:userId" is the ONLY identity source — it must be what the
        // emitter is registered under.
        NotificationController c = controller();
        when(valueOps.getAndDelete(anyString())).thenReturn("beta:USER-9");
        when(registry.register(anyString(), anyString())).thenReturn(new SseEmitter());

        c.stream("T2");

        verify(registry).register("beta", "USER-9");
        verify(registry, never()).register(eq("acme"), anyString());
    }

    @Test
    void stream_returnsADeadEmitterForAnUnknownTicket() {
        NotificationController c = controller();
        when(valueOps.getAndDelete(anyString())).thenReturn(null);

        SseEmitter emitter = c.stream("bogus");

        assertThat(emitter).isNotNull();
        // No registration for an unauthenticated caller.
        verify(registry, never()).register(anyString(), anyString());
    }

    @Test
    void stream_replayOfAConsumedTicketGetsNothing() {
        NotificationController c = controller();
        // First call wins, second sees null (GETDEL already removed it).
        when(valueOps.getAndDelete("notif:sse-ticket:T3"))
                .thenReturn("acme:USER-1")
                .thenReturn(null);
        when(registry.register(anyString(), anyString())).thenReturn(new SseEmitter());

        c.stream("T3");
        c.stream("T3");

        // Exactly one stream handed out for one ticket.
        verify(registry).register("acme", "USER-1");
    }
}
