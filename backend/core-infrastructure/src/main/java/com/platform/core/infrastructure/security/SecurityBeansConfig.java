package com.platform.core.infrastructure.security;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import com.platform.core.infrastructure.web.CoreRequestContextFilter;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Configuration
public class SecurityBeansConfig {

    private final AppSecurityProperties props;

    public SecurityBeansConfig(AppSecurityProperties props) {
        this.props = props;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Disable Spring Boot's automatic servlet-global registration of our
     * @Component filters that {@link com.platform.core.infrastructure.security.SecurityConfig}
     * adds into the Spring Security chain via {@code addFilterAfter(...)}.
     *
     * <p>Without these registrations, every {@code OncePerRequestFilter}
     * annotated {@code @Component} would be registered TWICE:
     * <ul>
     *   <li>once at the servlet container level (runs BEFORE Spring Security,
     *       so {@code SecurityContextHolder.getAuthentication()} returns null)</li>
     *   <li>once inside the Spring Security chain (would see the auth, but
     *       {@code OncePerRequestFilter} skips because of the per-request
     *       attribute set by the first invocation)</li>
     * </ul>
     *
     * <p>Net effect of the double registration: filters that read the JWT
     * (e.g. {@code CoreRequestContextFilter} setting {@code RequestContext.userId})
     * always see an empty auth context, so {@code RequestContext.userId()}
     * is null even after a valid Bearer token was accepted. Disabling the
     * global registration keeps only the Spring Security chain instance,
     * which runs AFTER {@code BearerTokenAuthenticationFilter} as intended.
     */
    @Bean
    public FilterRegistrationBean<CoreRequestContextFilter> coreRequestContextFilterRegistration(
            CoreRequestContextFilter f) {
        return disable(f);
    }

    @Bean
    public FilterRegistrationBean<ForceLogoutFilter> forceLogoutFilterRegistration(
            ForceLogoutFilter f) {
        return disable(f);
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            AuthRateLimitFilter f) {
        return disable(f);
    }

    private static <T extends Filter> FilterRegistrationBean<T> disable(T filter) {
        FilterRegistrationBean<T> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /**
     * JwtEncoder stays HS256 / shared-secret because it's used to sign tokens
     * locally minted by {@code AdminAuthController} (the legacy / break-glass
     * path). When the OIDC flow is the only one in use, this bean is unused
     * but stays cheap.
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        OctetSequenceKey jwk = buildJwk();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk));
        return new NimbusJwtEncoder(source);
    }

    /**
     * The JwtDecoder bean wired into the resource-server filter and the
     * fallback decode path in {@code CoreRequestContextFilter}.
     *
     * <p>In {@code oidc} mode this is a {@link DualModeJwtDecoder} that
     * routes:
     * <ul>
     *   <li>{@code alg=HS256} → in-house HMAC decoder (validates tokens
     *       minted by {@code AdminAuthController.login} — the break-glass
     *       password path that survives Keycloak being unavailable)</li>
     *   <li>everything else (typically {@code RS256}) → Keycloak JWKS
     *       decoder for normal SSO traffic. Pick by config:
     *       <ul>
     *         <li>{@code app.security.oidc.issuer-base-uri} set →
     *             {@link MultiRealmJwtDecoder} accepting any realm under
     *             that Keycloak host (recommended — SaaS multi-tenant).</li>
     *         <li>else {@code app.security.oidc.issuer-uri} set → a single
     *             {@link JwtDecoders#fromIssuerLocation} pinned to exactly
     *             that realm (locked-down single-tenant deploy).</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * <p>In {@code jwt} or {@code permit-all} mode there is no IdP and the
     * decoder is HS256 only.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.security.mode:permit-all}") String mode,
            @Value("${app.security.oidc.issuer-uri:}") String issuerUri,
            @Value("${app.security.oidc.issuer-base-uri:}") String issuerBaseUri) {
        JwtDecoder hs256 = buildHs256Decoder();
        if (!"oidc".equalsIgnoreCase(mode)) {
            return hs256;
        }
        JwtDecoder oidc;
        if (issuerBaseUri != null && !issuerBaseUri.isBlank()) {
            // Multi-tenant: any realm under this Keycloak host is trusted,
            // each realm's JWKS is fetched lazily on first sighting.
            oidc = new MultiRealmJwtDecoder(issuerBaseUri);
        } else if (issuerUri != null && !issuerUri.isBlank()) {
            // Single-tenant pinning — keep old behavior for staging / locked-down deploys.
            oidc = JwtDecoders.fromIssuerLocation(issuerUri);
        } else {
            throw new IllegalStateException(
                    "app.security.mode=oidc requires either app.security.oidc.issuer-base-uri "
                            + "(multi-realm, recommended) or app.security.oidc.issuer-uri "
                            + "(single realm) to be set.");
        }
        return new DualModeJwtDecoder(hs256, oidc);
    }

    private JwtDecoder buildHs256Decoder() {
        byte[] secret = effectiveSecret();
        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private OctetSequenceKey buildJwk() {
        byte[] secret = effectiveSecret();
        return new OctetSequenceKey.Builder(secret).keyID(UUID.randomUUID().toString()).build();
    }

    private byte[] effectiveSecret() {
        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            secret = "dev-placeholder-secret-please-override-32bytes!";
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            return padded;
        }
        return bytes;
    }
}
