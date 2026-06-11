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
        String mode = props.mode();

        // The HS256 / shared-secret decoder is wired whenever the mode is NOT
        // permit-all: it is the sole decoder in `jwt` mode AND the break-glass
        // branch of DualModeJwtDecoder in `oidc` mode (see SecurityBeansConfig).
        // So the secret MUST be enforced in both — not just `jwt`. Skipping the
        // check in `oidc` (the default prod mode) let the decoder silently fall
        // back to the committed placeholder secret, so anyone with the repo
        // could forge an HS256 token and impersonate any user / tenant. Only
        // `permit-all` (no auth at all, dev-only naked run) is exempt.
        boolean hs256Active = "jwt".equalsIgnoreCase(mode) || "oidc".equalsIgnoreCase(mode);
        if (!hs256Active) return;

        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.security.mode=" + mode + " uses the HS256 break-glass decoder but "
                    + "app.security.jwt.secret is missing. Set CORE_JWT_SECRET environment "
                    + "variable (>=32 bytes).");
        }
        if (secret.startsWith("dev-placeholder")) {
            throw new IllegalStateException(
                    "app.security.mode=" + mode + " uses the HS256 break-glass decoder but "
                    + "jwt.secret is still the dev placeholder — anyone with the source could "
                    + "forge tokens. Set CORE_JWT_SECRET environment variable. Active profiles: "
                    + activeProfiles);
        }
        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be >=32 bytes for HS256 (got " + len + " bytes).");
        }
    }
}
