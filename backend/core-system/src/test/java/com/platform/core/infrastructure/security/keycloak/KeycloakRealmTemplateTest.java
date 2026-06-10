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
}
