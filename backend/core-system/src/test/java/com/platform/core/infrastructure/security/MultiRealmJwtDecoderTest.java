package com.platform.core.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the trust-prefix routing semantics that {@link MultiRealmJwtDecoder}
 * has to get right for multi-tenant safety:
 *
 *   1. Reject anything whose iss is not under the configured Keycloak base
 *      — that's the perimeter we trust the KC admin to control.
 *   2. For trusted issuers, dispatch to the per-realm decoder; the actual
 *      signature + claims validation lives there.
 *   3. Cache per-issuer so the discovery / JWKS roundtrip happens once per
 *      realm, not once per request.
 *   4. Don't cache negative lookups — if Keycloak is briefly unreachable,
 *      a subsequent request must be free to retry.
 */
class MultiRealmJwtDecoderTest {

    /**
     * Build a tokens with the given iss in its payload. Signature byte is
     * arbitrary — we never validate it in these tests (the stub decoder
     * factory short-circuits validation).
     */
    private static String tokenWithIssuer(String iss) {
        String header  = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String payload = "{\"iss\":\"" + iss + "\",\"sub\":\"kc-uuid\",\"tid\":\"acme\"}";
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "." + enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + ".sig-not-checked";
    }

    private static Jwt stubJwt(String iss) {
        return new Jwt(
                "header.payload.sig",
                Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("iss", iss, "sub", "kc-uuid", "tid", "acme"));
    }

    @Test
    void peekIssuer_extractsIssClaim() {
        String token = tokenWithIssuer("http://kc.local/realms/acme");
        assertThat(MultiRealmJwtDecoder.peekIssuer(token))
                .isEqualTo("http://kc.local/realms/acme");
    }

    @Test
    void peekIssuer_returnsNullForMalformedToken() {
        assertThat(MultiRealmJwtDecoder.peekIssuer(null)).isNull();
        assertThat(MultiRealmJwtDecoder.peekIssuer("")).isNull();
        assertThat(MultiRealmJwtDecoder.peekIssuer("not.a.token-no-iss-claim")).isNull();
        assertThat(MultiRealmJwtDecoder.peekIssuer("no-dots-here")).isNull();
    }

    @Test
    void rejectsTokenWhoseIssuerIsNotUnderTrustedBase() {
        MultiRealmJwtDecoder decoder = new MultiRealmJwtDecoder(
                "http://kc.local",
                iss -> { throw new IllegalStateException("decoder must not be built for untrusted iss"); });

        String evilToken = tokenWithIssuer("http://attacker.example/realms/evil");

        assertThatThrownBy(() -> decoder.decode(evilToken))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("trusted Keycloak base");
    }

    @Test
    void rejectsTokenWithNoIssClaim() {
        MultiRealmJwtDecoder decoder = new MultiRealmJwtDecoder("http://kc.local",
                iss -> { throw new IllegalStateException("not reachable"); });
        // Header + payload {"sub":"x"} (no iss).
        String token = "eyJhbGciOiJSUzI1NiJ9." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8)) + ".sig";

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("iss");
    }

    @Test
    void routesTrustedIssuerToPerRealmDecoder() {
        Map<String, JwtDecoder> stubs = new HashMap<>();
        JwtDecoder forAcme = mock(JwtDecoder.class);
        when(forAcme.decode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(stubJwt("http://kc.local/realms/acme"));
        stubs.put("http://kc.local/realms/acme", forAcme);

        MultiRealmJwtDecoder decoder = new MultiRealmJwtDecoder("http://kc.local", stubs::get);

        Jwt result = decoder.decode(tokenWithIssuer("http://kc.local/realms/acme"));

        assertThat(result.getClaimAsString("iss")).isEqualTo("http://kc.local/realms/acme");
    }

    @Test
    void cachesPerRealmDecoder_onlyBuildsOncePerIssuer() {
        AtomicInteger builds = new AtomicInteger();
        JwtDecoder forAcme = mock(JwtDecoder.class);
        when(forAcme.decode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(stubJwt("http://kc.local/realms/acme"));

        MultiRealmJwtDecoder decoder = new MultiRealmJwtDecoder("http://kc.local", iss -> {
            builds.incrementAndGet();
            return forAcme;
        });

        String token = tokenWithIssuer("http://kc.local/realms/acme");
        decoder.decode(token);
        decoder.decode(token);
        decoder.decode(token);

        assertThat(builds.get())
                .as("per-issuer decoder must be built once and cached")
                .isEqualTo(1);
    }

    @Test
    void differentRealmsGetDifferentCachedDecoders() {
        AtomicInteger builds = new AtomicInteger();
        MultiRealmJwtDecoder decoder = new MultiRealmJwtDecoder("http://kc.local", iss -> {
            builds.incrementAndGet();
            JwtDecoder d = mock(JwtDecoder.class);
            when(d.decode(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(stubJwt(iss));
            return d;
        });

        decoder.decode(tokenWithIssuer("http://kc.local/realms/acme"));
        decoder.decode(tokenWithIssuer("http://kc.local/realms/beta"));
        decoder.decode(tokenWithIssuer("http://kc.local/realms/acme"));
        decoder.decode(tokenWithIssuer("http://kc.local/realms/beta"));

        assertThat(builds.get())
                .as("one decoder per realm, cached")
                .isEqualTo(2);
    }

    @Test
    void trustedBaseStripsTrailingSlash() {
        // The base may be configured with or without a trailing slash;
        // both must accept the exact same set of issuers.
        JwtDecoder stub = mock(JwtDecoder.class);
        when(stub.decode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(stubJwt("http://kc.local/realms/acme"));

        MultiRealmJwtDecoder withSlash = new MultiRealmJwtDecoder("http://kc.local/", iss -> stub);
        MultiRealmJwtDecoder noSlash    = new MultiRealmJwtDecoder("http://kc.local",  iss -> stub);

        String token = tokenWithIssuer("http://kc.local/realms/acme");
        // Neither should throw — both must accept the token.
        withSlash.decode(token);
        noSlash.decode(token);
    }

    @Test
    void rejectsBlankTrustedBase() {
        assertThatThrownBy(() -> new MultiRealmJwtDecoder(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MultiRealmJwtDecoder(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MultiRealmJwtDecoder("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
