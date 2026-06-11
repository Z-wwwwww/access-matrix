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

    // Substrings that mark a secret as a committed/template value, matched
    // case-insensitively ANYWHERE in the secret — not just as a prefix. The
    // prefix-only "dev-placeholder" check let the .env.example throwaway key
    // ("dev-local-hs256-break-glass-key-32B+throwaway-do-not-use-in-prod")
    // through: it isn't the placeholder and is >=32 bytes, so copying the env
    // template into prod verbatim yielded a forgeable-token deployment that
    // only ops discipline prevented. A real secret is `openssl rand -base64`
    // output and can never contain these words, so false positives are moot.
    private static final List<String> WEAK_SECRET_MARKERS = List.of(
            "dev-placeholder", "dev-local", "throwaway", "change-in-prod", "do-not-use");

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
        String lower = secret.toLowerCase(java.util.Locale.ROOT);
        for (String marker : WEAK_SECRET_MARKERS) {
            if (lower.contains(marker)) {
                throw new IllegalStateException(
                        "app.security.mode=" + mode + " uses the HS256 break-glass decoder but "
                        + "jwt.secret contains the placeholder marker \"" + marker + "\" — it is a "
                        + "committed template value, so anyone with the source could forge tokens. "
                        + "Set CORE_JWT_SECRET to a fresh random value (openssl rand -base64 48), "
                        + "in dev too. Active profiles: " + activeProfiles);
            }
        }
        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be >=32 bytes for HS256 (got " + len + " bytes).");
        }
    }
}
