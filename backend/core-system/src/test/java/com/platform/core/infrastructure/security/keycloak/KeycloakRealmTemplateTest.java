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

    /**
     * The SPA client is a PUBLIC client running the authorization-code flow, so
     * Keycloak must be told to REQUIRE PKCE on it.
     *
     * <p>The browser half is already there — {@code utils/oidc.js} sends
     * {@code code_challenge_method: 'S256'} on every {@code /authorize} and the
     * verifier on the exchange. But an unset {@code pkce.code.challenge.method}
     * means Keycloak merely *accepts* PKCE instead of *demanding* it: a code
     * obtained by any other means can still be redeemed at the token endpoint
     * with no verifier at all, which is the authorization-code interception
     * attack PKCE exists to close. A public client has no client secret, so PKCE
     * is the only thing binding the code to the browser that started the flow.
     *
     * <p>Same class of gap — and same fix shape — as
     * {@link #renderRealmJson_keepsBruteForceProtectionOn()}: a security control
     * that only exists if the realm template pins it, because
     * {@code KeycloakRealmService} clones this template for every new tenant.
     *
     * <p>Safe to enforce: the only authorization-code consumer of this client is
     * the SPA (already S256). The backend is a resource server (no
     * {@code oauth2Login}), Swagger uses a plain HTTP-bearer scheme, and
     * {@code OidcJitProvisioningIT} authenticates with the password grant —
     * PKCE does not apply to any of those.
     *
     * <p><b>Asserted on the parsed client, not on the raw text.</b> Keycloak's
     * own {@code account-console} and {@code security-admin-console} ship with
     * {@code S256} already, so a whole-document {@code contains} passes even when
     * OUR client has no such attribute — a false green this test was written with
     * and had to be tightened out of. (That the built-ins enforce it while the
     * app's client did not is itself the tell that this was an oversight.)
     */
    @Test
    void renderRealmJson_requiresPkceOnThePublicSpaClient() throws Exception {
        KeycloakRealmService svc = new KeycloakRealmService(null, null);
        Method m = KeycloakRealmService.class.getDeclaredMethod(
                "renderRealmJson", String.class, String.class);
        m.setAccessible(true);

        String out = (String) m.invoke(svc, "acme", "Acme Inc");

        var root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(out);
        var spa = java.util.stream.StreamSupport.stream(root.get("clients").spliterator(), false)
                .filter(c -> "access-matrix-backend".equals(c.get("clientId").asString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("access-matrix-backend client missing from the template"));

        // Why PKCE is load-bearing here: public client + authorization-code flow.
        assertThat(spa.get("publicClient").asBoolean()).isTrue();
        assertThat(spa.get("standardFlowEnabled").asBoolean()).isTrue();

        var attrs = spa.get("attributes");
        assertThat(attrs).as("client has no attributes block at all").isNotNull();
        assertThat(attrs.get("pkce.code.challenge.method"))
                .as("the public SPA client must REQUIRE PKCE, not merely tolerate it")
                .isNotNull();
        assertThat(attrs.get("pkce.code.challenge.method").asString()).isEqualTo("S256");
    }
}
