package com.platform.core.infrastructure.security;

import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-IP throttle in front of {@code /auth/*}.
 *
 * <p>The store used to be a plain {@code ConcurrentHashMap<String, Bucket>} that
 * nothing ever removed from: every distinct client IP that touched an
 * {@code /auth/} path left a bucket behind for the life of the JVM. On a
 * public-facing deployment that grows without bound with ordinary traffic, and an
 * attacker rotating source addresses (or spoofed {@code X-Forwarded-For} values
 * when {@code trust-forwarded-headers=true}) turns the throttle itself into a
 * cheap memory-exhaustion vector. These tests pin the bound AND the throttling
 * behaviour it must not weaken.
 */
class AuthRateLimitFilterTest {

    private static final int PER_MINUTE = 5;

    private static AuthRateLimitFilter filter() {
        AppSecurityProperties props = new AppSecurityProperties(
                null, null,
                new AppSecurityProperties.RateLimit(true, PER_MINUTE, Duration.ofMinutes(1)),
                null, null, null);
        return new AuthRateLimitFilter(props, JsonMapper.builder().build(),
                new ClientIpResolver(false));
    }

    private static MockHttpServletRequest authRequestFrom(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }

    /** Runs one request through the filter and returns the response. */
    private static MockHttpServletResponse call(AuthRateLimitFilter f, String ip) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = (rq, rs) -> { };
        f.doFilter(authRequestFrom(ip), resp, chain);
        return resp;
    }

    @Test
    void allows_up_to_the_configured_budget_then_429s_the_same_ip() throws Exception {
        AuthRateLimitFilter f = filter();

        for (int i = 0; i < PER_MINUTE; i++) {
            assertThat(call(f, "203.0.113.7").getStatus())
                    .as("request %s of the budget", i + 1)
                    .isEqualTo(200);
        }

        MockHttpServletResponse over = call(f, "203.0.113.7");
        assertThat(over.getStatus()).isEqualTo(429);
        assertThat(over.getHeader("Retry-After")).isNotNull();
        assertThat(over.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void one_exhausted_ip_does_not_throttle_another() throws Exception {
        AuthRateLimitFilter f = filter();
        for (int i = 0; i <= PER_MINUTE; i++) call(f, "203.0.113.7");

        assertThat(call(f, "203.0.113.8").getStatus()).isEqualTo(200);
    }

    @Test
    void non_auth_paths_are_not_filtered_at_all() throws Exception {
        AuthRateLimitFilter f = filter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/user/me");
        req.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        for (int i = 0; i < PER_MINUTE * 10; i++) {
            f.doFilter(req, resp, (rq, rs) -> { });
        }

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(f.trackedIpCount()).isZero();
    }

    @Test
    void the_bucket_store_stays_bounded_under_ip_rotation() throws Exception {
        AuthRateLimitFilter f = filter();

        // Well past the 100k cap, as an address-rotating attacker would drive it.
        // Request/response objects are reused (only the peer address changes) —
        // OncePerRequestFilter clears its own marker attribute in a finally block,
        // so replaying one request instance is equivalent to fresh ones here.
        int distinctIps = 150_000;
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = (rq, rs) -> { };
        for (int i = 0; i < distinctIps; i++) {
            req.setRemoteAddr("10." + (i >> 16 & 0xFF) + "." + (i >> 8 & 0xFF) + "." + (i & 0xFF));
            f.doFilter(req, resp, chain);
        }

        long tracked = f.trackedIpCount();
        assertThat(tracked)
                .as("every distinct IP kept its bucket forever — unbounded growth")
                .isLessThan(distinctIps);
        // Caffeine's size-eviction is approximate; the point is the store is
        // capped near maximumSize rather than tracking the whole rotation.
        assertThat(tracked).isLessThan(120_000L);
    }
}
