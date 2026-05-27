package com.platform.core.infrastructure.security;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link JwtDecoder} that accepts tokens from <em>any Keycloak realm</em>
 * sharing a common base URL — the SaaS multi-tenant story where one Keycloak
 * instance hosts one realm per tenant.
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Peek the JWT payload (no signature check yet) to read the {@code iss}
 *       claim.</li>
 *   <li>Reject anything whose {@code iss} doesn't start with
 *       {@code <trustedBase>/realms/} — that's the perimeter we trust the
 *       Keycloak admin to control.</li>
 *   <li>Look up (or build, on first sighting) a real {@link JwtDecoder} for
 *       that specific issuer via {@link JwtDecoders#fromIssuerLocation} — it
 *       does the OIDC discovery dance, wires JWKS validation, and pins the
 *       issuer assertion to the exact realm URL. Cache per issuer so we pay
 *       the discovery cost once per realm.</li>
 *   <li>Delegate the actual signature + claims validation to that decoder.</li>
 * </ol>
 *
 * <p>The peek-then-validate split is necessary because Spring's stock
 * decoders are bound to a single issuer URI at construction time; we have
 * to know which realm a token claims to come from before we can pick the
 * right decoder. The peek itself is just a base64 decode of the payload
 * segment and a regex on {@code iss} — no signature trust is implied.
 * Even if an attacker forges the iss claim, step 2 either rejects it
 * (untrusted base) or routes it to the right realm's JWKS, which will
 * reject the forged signature.
 *
 * <p>Cache safety: {@link ConcurrentHashMap#computeIfAbsent} is safe under
 * contention; if discovery transiently fails the entry is never put,
 * the next request retries. We do NOT cache negative lookups, so a
 * Keycloak that's briefly unreachable doesn't permanently 401 every user.
 */
public class MultiRealmJwtDecoder implements JwtDecoder {

    /** Trusted base — e.g. {@code http://localhost:8180}. No trailing slash. */
    private final String trustedBase;
    private final ConcurrentMap<String, JwtDecoder> decoderByIssuer = new ConcurrentHashMap<>();
    private final Function<String, JwtDecoder> decoderFactory;

    public MultiRealmJwtDecoder(String trustedBase) {
        this(trustedBase, JwtDecoders::fromIssuerLocation);
    }

    /** Constructor for tests — lets us swap discovery for a stub. */
    MultiRealmJwtDecoder(String trustedBase, Function<String, JwtDecoder> decoderFactory) {
        if (trustedBase == null || trustedBase.isBlank()) {
            throw new IllegalArgumentException("trustedBase must not be blank");
        }
        this.trustedBase = stripTrailingSlash(trustedBase);
        this.decoderFactory = decoderFactory;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String iss = peekIssuer(token);
        if (iss == null) {
            throw new BadJwtException("token has no readable iss claim");
        }
        if (!iss.startsWith(trustedBase + "/realms/")) {
            // Don't leak the rejected issuer in the message — it's attacker-
            // controlled and might end up in logs. The base is fine to mention
            // because it's a server-config value, not a token value.
            throw new BadJwtException(
                    "token issuer is not under the trusted Keycloak base " + trustedBase);
        }
        JwtDecoder delegate = decoderByIssuer.computeIfAbsent(iss, decoderFactory);
        return delegate.decode(token);
    }

    private static final Pattern ISS_PATTERN =
            Pattern.compile("\"iss\"\\s*:\\s*\"([^\"\\\\]+)\"");

    /**
     * Read the {@code iss} claim out of the JWT payload <em>without
     * validating the signature</em>. Used only to pick which underlying
     * decoder to delegate to; the chosen decoder still runs full validation.
     *
     * <p>We don't pull in Jackson for this — the payload is a small JSON
     * object and {@code iss} is a URL (no escaped quotes), so a regex on
     * the base64-decoded payload is precise enough and avoids touching
     * the autoconfigured {@code JsonMapper} bean from the hot path.
     */
    static String peekIssuer(String token) {
        if (token == null) return null;
        int firstDot = token.indexOf('.');
        if (firstDot <= 0) return null;
        int secondDot = token.indexOf('.', firstDot + 1);
        if (secondDot <= firstDot + 1) return null;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder()
                    .decode(token.substring(firstDot + 1, secondDot));
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            Matcher m = ISS_PATTERN.matcher(payload);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
