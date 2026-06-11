package com.platform.core.infrastructure.security;

import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the fix for the HS256 break-glass secret bypass: the shared-secret
 * decoder is wired in BOTH {@code oidc} and {@code jwt} modes, so the secret
 * must be enforced in both — not only {@code jwt}. Skipping {@code oidc} (the
 * default prod mode) let the decoder fall back to the committed placeholder,
 * making every HS256 token forgeable by anyone holding the source.
 */
class JwtSecretValidatorTest {

    private static final String PLACEHOLDER = "dev-placeholder-secret-please-override-32bytes!";
    /** The committed .env.example value — >=32 bytes and not the placeholder prefix, so it
     *  slipped past the original prefix-only check; copying the template into prod verbatim
     *  produced a forgeable-token deployment. */
    private static final String ENV_EXAMPLE_THROWAWAY =
            "dev-local-hs256-break-glass-key-32B+throwaway-do-not-use-in-prod";
    private static final String VALID = "a-real-strong-secret-of-at-least-32-bytes-long";

    private JwtSecretValidator validator(String mode, String secret) {
        AppSecurityProperties props = new AppSecurityProperties(
                mode,
                new AppSecurityProperties.Jwt(secret, "tid", "sub", "preferred_username", "scope"),
                null, null, null, null);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"test"});
        return new JwtSecretValidator(props, env);
    }

    @Test
    @DisplayName("oidc mode rejects the committed placeholder secret (the forge-token bug)")
    void oidc_rejectsPlaceholder() {
        assertThatThrownBy(() -> validator("oidc", PLACEHOLDER).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    @DisplayName("oidc mode rejects a missing secret")
    void oidc_rejectsMissing() {
        assertThatThrownBy(() -> validator("oidc", null).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("oidc mode rejects a too-short secret (<32 bytes)")
    void oidc_rejectsShort() {
        assertThatThrownBy(() -> validator("oidc", "short").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(">=32 bytes");
    }

    @Test
    @DisplayName("oidc mode rejects the committed .env.example throwaway key (template-copied-to-prod)")
    void oidc_rejectsEnvExampleThrowaway() {
        assertThatThrownBy(() -> validator("oidc", ENV_EXAMPLE_THROWAWAY).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("template value");
    }

    @Test
    @DisplayName("jwt mode rejects the committed .env.example throwaway key too")
    void jwt_rejectsEnvExampleThrowaway() {
        assertThatThrownBy(() -> validator("jwt", ENV_EXAMPLE_THROWAWAY).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("template value");
    }

    @Test
    @DisplayName("weak markers are caught anywhere in the secret, not only as a prefix")
    void rejectsMarkerInMiddle() {
        assertThatThrownBy(() -> validator("oidc",
                "padding-padding-padding-change-in-prod-padding-padding").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("change-in-prod");
    }

    @Test
    @DisplayName("weak markers are matched case-insensitively")
    void rejectsMarkerCaseInsensitive() {
        assertThatThrownBy(() -> validator("oidc",
                "SOME-LONG-ENOUGH-SECRET-VALUE-THROWAWAY-1234567890").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("throwaway");
    }

    @Test
    @DisplayName("oidc mode accepts a strong non-placeholder secret")
    void oidc_acceptsValid() {
        assertThatCode(() -> validator("oidc", VALID).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("jwt mode still rejects the placeholder (regression)")
    void jwt_rejectsPlaceholder() {
        assertThatThrownBy(() -> validator("jwt", PLACEHOLDER).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    @DisplayName("permit-all mode is exempt (no auth enforced — HS256 path irrelevant)")
    void permitAll_exempt() {
        assertThatCode(() -> validator("permit-all", PLACEHOLDER).validate()).doesNotThrowAnyException();
    }
}
