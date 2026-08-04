package com.platform.core.infrastructure.security.keycloak;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the realm-clone template rendering: besides the realm name /
 * displayName / tid-mapper claim, the built-in client URLs MUST be
 * retargeted too. The template is demo's export, so the account /
 * account-console clients carry {@code /realms/demo/account/*} redirect
 * URIs and the realm admin console {@code /admin/demo/console/*} — left
 * unreplaced, every cloned tenant realm rejects its own account console
 * with "invalid redirect_uri" (the in-app "Change password" entry lands
 * exactly there).
 */
class KeycloakRealmTemplateTest {

    @Test
    void renderRealmJson_retargetsBuiltInClientUrls() throws Exception {
        KeycloakRealmService svc = new KeycloakRealmService(null, null);
        Method m = KeycloakRealmService.class.getDeclaredMethod(
                "renderRealmJson", String.class, String.class);
        m.setAccessible(true);

        String out = (String) m.invoke(svc, "acme", "Acme Inc");

        assertThat(out).contains("\"realm\": \"acme\"");
        assertThat(out).contains("\"displayName\": \"Acme Inc\"");
        assertThat(out).contains("\"claim.value\": \"acme\"");
        // Built-in client URLs follow the new realm — no demo leftovers.
        assertThat(out).contains("/realms/acme/account/*");
        assertThat(out).contains("/admin/acme/console/");
        assertThat(out).doesNotContain("/realms/demo/");
        assertThat(out).doesNotContain("/admin/demo/");
    }

    /**
     * Every cloned business realm MUST have Keycloak's brute-force detection on.
     *
     * <p>This is the only failed-login throttle that covers the path business
     * users actually authenticate through. In oidc mode the login form is
     * Keycloak's, so neither app-side defence applies: {@code AccountLockoutService}
     * only fires from {@code AuthService.login} (i.e. the {@code POST /auth/login}
     * break-glass path) and {@code AuthRateLimitFilter} only filters URIs
     * containing {@code /auth/} on our own server — never KC's token endpoint.
     * With the switch off, a tenant's passwords face unlimited online guessing.
     *
     * <p>The template is demo's realm export, so this asserts on the rendered
     * clone — the exact JSON {@code kc.realms().create()} receives.
     */
    @Test
    void renderRealmJson_keepsBruteForceProtectionOn() throws Exception {
        KeycloakRealmService svc = new KeycloakRealmService(null, null);
        Method m = KeycloakRealmService.class.getDeclaredMethod(
                "renderRealmJson", String.class, String.class);
        m.setAccessible(true);

        String out = (String) m.invoke(svc, "acme", "Acme Inc");

        assertThat(out.replaceAll("\\s+", ""))
                .as("cloned realms must not ship with brute-force detection disabled")
                .contains("\"bruteForceProtected\":true");
    }
}
