package com.platform.core.infrastructure.security;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
     * OIDC decoder — active when {@code app.security.mode=oidc}. Pulls the
     * well-known discovery document from {@code issuer-uri} and configures
     * JWKS validation + issuer assertion automatically.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
    public JwtDecoder oidcJwtDecoder(
            @Value("${app.security.oidc.issuer-uri:}") String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException(
                    "app.security.mode=oidc requires app.security.oidc.issuer-uri to be set "
                            + "(e.g. http://localhost:8180/realms/default).");
        }
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    /**
     * Fallback HS256 decoder for tokens locally signed by {@link #jwtEncoder()} —
     * the {@code AdminAuthController.login} (legacy / break-glass) path. Only
     * registered when no other {@link JwtDecoder} is on the context, so the
     * OIDC bean above wins when mode=oidc.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder hs256JwtDecoder() {
        return buildHs256Decoder();
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
