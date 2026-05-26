package com.platform.core.infrastructure.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * {@link JwtDecoder} that routes by the JWT's {@code alg} header to one of
 * two underlying decoders, enabling the "Keycloak OIDC + in-house HS256
 * break-glass" dual-mode setup.
 *
 * <p>Routing rule:
 * <ul>
 *   <li>{@code alg = HS256} → in-house decoder (HMAC, shared secret).
 *       Tokens minted by {@code AdminAuthController.login}.</li>
 *   <li>anything else (typically {@code RS256}) → OIDC decoder
 *       (JWKS-backed, validates against the IdP's well-known doc).</li>
 * </ul>
 *
 * <p>Why route on {@code alg} rather than {@code iss}: the alg sits in
 * the JWT header so we can pick it without decoding the body, and any
 * RS256 token must be IdP-issued (we never sign RS256 ourselves) while
 * any HS256 token must be in-house (the OIDC IdP never signs HS256 for us).
 * The alg header is also what each underlying decoder will subsequently
 * verify, so a mismatched routing simply lands on a decoder that rejects
 * the token — no chance of a token being accepted by the "wrong" path.
 */
public class DualModeJwtDecoder implements JwtDecoder {

    private final JwtDecoder hs256Decoder;
    private final JwtDecoder oidcDecoder;

    public DualModeJwtDecoder(JwtDecoder hs256Decoder, JwtDecoder oidcDecoder) {
        this.hs256Decoder = hs256Decoder;
        this.oidcDecoder = oidcDecoder;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        if (isHs256(token)) {
            return hs256Decoder.decode(token);
        }
        return oidcDecoder.decode(token);
    }

    /**
     * Peek the JWT header and check {@code alg}. We do a cheap manual
     * parse here rather than feeding the token to a Nimbus parser —
     * the cost would be a wasted full decode that the routed decoder is
     * about to do anyway.
     */
    private static boolean isHs256(String token) {
        if (token == null) return false;
        int firstDot = token.indexOf('.');
        if (firstDot <= 0) return false;
        try {
            String headerJson = new String(
                    Base64.getUrlDecoder().decode(token.substring(0, firstDot)),
                    StandardCharsets.UTF_8);
            // Header is a small JSON like {"alg":"HS256","typ":"JWT"}.
            // String-contains is good enough: alg values are tightly enumerated
            // (HS256 / RS256 / ES256 / ...) and don't appear elsewhere in the
            // header. Avoids dragging Jackson into a hot path.
            return headerJson.contains("\"alg\"")
                    && headerJson.contains("\"HS256\"");
        } catch (Exception e) {
            // Malformed header — let the OIDC decoder reject the token uniformly.
            return false;
        }
    }
}
