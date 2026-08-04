package com.platform.system.web;

import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.security.OidcUserResolver;
import com.platform.core.infrastructure.web.CoreRequestContextFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The caller-supplied {@code X-Trace-Id} must be clamped to the width of
 * {@code core_domain_event.trace_id} (64) before it enters
 * {@link RequestContext}.
 *
 * <p>Why this one is not "just a lost audit row": {@code OutboxEventPublisher}
 * inserts the outbox row inside the caller's transaction on purpose — that IS
 * the transactional-outbox guarantee — and it copies {@code trace_id} straight
 * out of RequestContext. Verified against the real DB that a 65-char trace_id
 * is rejected ("value too long for type character varying(64)") while 64 is
 * accepted, so an over-long header made the INSERT fail and rolled back the
 * whole business write. Any gateway or APM agent that stamps a long correlation
 * id would break every event-publishing state change with an opaque 500.
 */
@ExtendWith(MockitoExtension.class)
class CoreRequestContextFilterTraceIdTest {

    @Mock JwtDecoder jwtDecoder;

    @SuppressWarnings("unchecked")
    private CoreRequestContextFilter filter() {
        ObjectProvider<OidcUserResolver> provider = mock(ObjectProvider.class);
        return new CoreRequestContextFilter(provider, jwtDecoder);
    }

    /** Filter wired with a resolver that answers {@code businessId} for any token. */
    @SuppressWarnings("unchecked")
    private CoreRequestContextFilter filterWithResolver(String businessId) {
        ObjectProvider<OidcUserResolver> provider = mock(ObjectProvider.class);
        OidcUserResolver resolver = jwt -> businessId;
        when(provider.getIfAvailable()).thenReturn(resolver);
        return new CoreRequestContextFilter(provider, jwtDecoder);
    }

    private static final String KC_UUID = "946f39a6-2693-4519-8014-c7bc146eaef7";

    /** A bearer request whose token decodes to the given Keycloak subject. */
    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/me/profile");
        req.addHeader("Authorization", "Bearer dummy.token.value");
        req.addHeader("X-Tenant-Id", "demo");
        return req;
    }

    private org.springframework.security.oauth2.jwt.Jwt kcJwt() {
        return org.springframework.security.oauth2.jwt.Jwt.withTokenValue("dummy.token.value")
                .header("alg", "RS256")
                .subject(KC_UUID)
                .claim("tid", "demo")
                .claim("preferred_username", "alice")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void resolverRefusal_clearsTheUser_insteadOfLeavingTheKeycloakSubject() throws Exception {
        // core_oplog.user_id is character(26); a 36-char KC UUID is rejected by that
        // column and OpLogService swallows the failure, so the request's audit row
        // vanished entirely. Every refusal branch documents its effect as "no business
        // user", so the context must actually say that.
        when(jwtDecoder.decode("dummy.token.value")).thenReturn(kcJwt());
        java.util.concurrent.atomic.AtomicReference<String> seen =
                new java.util.concurrent.atomic.AtomicReference<>("unset");

        filterWithResolver(null).doFilter(bearerRequest(), new MockHttpServletResponse(),
                (rq, rs) -> seen.set(RequestContext.userId()));

        assertThat(seen.get()).isNull();
    }

    @Test
    void resolverSuccess_stillPromotesTheBusinessId() throws Exception {
        when(jwtDecoder.decode("dummy.token.value")).thenReturn(kcJwt());
        java.util.concurrent.atomic.AtomicReference<String> seen =
                new java.util.concurrent.atomic.AtomicReference<>("unset");

        filterWithResolver("01ARZ3NDEKTSV4RRFFQ69G5FAV")
                .doFilter(bearerRequest(), new MockHttpServletResponse(),
                        (rq, rs) -> seen.set(RequestContext.userId()));

        assertThat(seen.get()).isEqualTo("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    }

    /** Runs the filter and returns the trace id observed INSIDE the chain. */
    private String traceIdSeenDownstream(String headerValue, MockHttpServletResponse resp) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/demo/tasks");
        if (headerValue != null) req.addHeader("X-Trace-Id", headerValue);

        AtomicReference<String> seen = new AtomicReference<>();
        filter().doFilter(req, resp, (rq, rs) -> seen.set(RequestContext.current().getTraceId()));
        return seen.get();
    }

    @Test
    void overlongTraceHeaderIsClampedToTheOutboxColumnWidth() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        String seen = traceIdSeenDownstream("T".repeat(200), resp);

        assertThat(seen).hasSize(64);
        // The echoed header must report what we actually recorded, otherwise the
        // client correlates against a value that is not in any of our rows.
        assertThat(resp.getHeader("X-Trace-Id")).isEqualTo(seen);
    }

    @Test
    void exactly64IsKeptWholeSinceTheColumnHoldsIt() throws Exception {
        String exact = "T".repeat(64);
        String seen = traceIdSeenDownstream(exact, new MockHttpServletResponse());

        assertThat(seen).isEqualTo(exact);
    }

    @Test
    void ordinaryTraceHeaderIsPassedThroughUnchanged() throws Exception {
        // W3C traceparent-shaped value (55 chars) — a realistic gateway stamp.
        String tp = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String seen = traceIdSeenDownstream(tp, new MockHttpServletResponse());

        assertThat(seen).isEqualTo(tp);
    }

    @Test
    void missingHeaderStillGetsAGeneratedTraceIdThatFits() throws Exception {
        String seen = traceIdSeenDownstream(null, new MockHttpServletResponse());

        assertThat(seen).hasSize(32);   // UUID hex, no dashes
    }

    @Test
    void blankHeaderIsTreatedAsMissing() throws Exception {
        String seen = traceIdSeenDownstream("   ", new MockHttpServletResponse());

        assertThat(seen).hasSize(32);
    }

    @Test
    void requestContextIsClearedAfterTheChain() throws Exception {
        traceIdSeenDownstream("T".repeat(200), new MockHttpServletResponse());

        assertThat(RequestContext.current()).isNull();
    }
}
