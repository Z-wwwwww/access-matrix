package com.platform.core.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the X-Forwarded-For spoofing fix: client-supplied forwarding headers
 * must be ignored by default (so they can't be rotated to bypass the per-IP
 * auth rate limiter or forge the audited / alerted IP), and when explicitly
 * trusted, only the rightmost (nearest-proxy) hop is used.
 */
class ClientIpResolverTest {

    private HttpServletRequest req(String xff, String xRealIp, String remote) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(r.getHeader("X-Real-IP")).thenReturn(xRealIp);
        when(r.getRemoteAddr()).thenReturn(remote);
        return r;
    }

    @Test
    @DisplayName("default (untrusted): ignores spoofed XFF / X-Real-IP, uses socket peer")
    void untrustedIgnoresHeaders() {
        ClientIpResolver r = new ClientIpResolver(false);
        assertThat(r.resolve(req("1.2.3.4", "5.6.7.8", "10.0.0.9"))).isEqualTo("10.0.0.9");
    }

    @Test
    @DisplayName("trusted: uses the rightmost XFF hop (proxy-observed), not the client-claimed leftmost")
    void trustedUsesRightmostHop() {
        ClientIpResolver r = new ClientIpResolver(true);
        // attacker prepends "1.1.1.1"; the real direct client our proxy saw is the last entry
        assertThat(r.resolve(req("1.1.1.1, 9.9.9.9", null, "10.0.0.9"))).isEqualTo("9.9.9.9");
    }

    @Test
    @DisplayName("trusted: falls back to X-Real-IP then socket peer when XFF absent")
    void trustedFallbacks() {
        ClientIpResolver r = new ClientIpResolver(true);
        assertThat(r.resolve(req(null, "5.6.7.8", "10.0.0.9"))).isEqualTo("5.6.7.8");
        assertThat(r.resolve(req(null, null, "10.0.0.9"))).isEqualTo("10.0.0.9");
    }

    @Test
    @DisplayName("null request resolves to null (audit-safe)")
    void nullRequest() {
        assertThat(new ClientIpResolver(false).resolve(null)).isNull();
    }
}
