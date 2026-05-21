package com.platform.core.infrastructure.security;

import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtSecretValidator {

    private final AppSecurityProperties props;
    private final Environment env;

    public JwtSecretValidator(AppSecurityProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @PostConstruct
    void validate() {
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!"jwt".equalsIgnoreCase(props.mode())) return;

        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.security.mode=jwt but app.security.jwt.secret is missing. " +
                    "Set CORE_JWT_SECRET environment variable (>=32 bytes).");
        }
        if (secret.startsWith("dev-placeholder")) {
            throw new IllegalStateException(
                    "app.security.mode=jwt but jwt.secret is still the dev placeholder. " +
                    "Set CORE_JWT_SECRET environment variable. Active profiles: " + activeProfiles);
        }
        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be >=32 bytes for HS256 (got " + len + " bytes).");
        }
    }
}
