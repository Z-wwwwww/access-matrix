package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server-side Keycloak Admin REST connection settings used by
 * {@code KeycloakUserService}.
 *
 * <p>Two authentication shapes are supported:
 * <ul>
 *   <li><b>service account</b> — {@link Admin#clientSecret()} set, no
 *       username/password. Recommended for prod: a dedicated client per
 *       realm with only {@code realm-management/manage-users} granted.</li>
 *   <li><b>password grant</b> — clientId=admin-cli + admin username/password.
 *       Convenient default for local dev so a freshly-launched Keycloak
 *       (admin/admin) just works without extra config.</li>
 * </ul>
 *
 * @param admin Keycloak Admin REST connection block (under {@code app.keycloak.admin}).
 */
@ConfigurationProperties(prefix = "app.keycloak")
public record AppKeycloakProperties(Admin admin) {

    public AppKeycloakProperties {
        if (admin == null) admin = new Admin(null, null, null, null, null, null);
    }

    /**
     * @param serverUrl    e.g. {@code http://localhost:8180}
     * @param realm        the realm the admin credentials authenticate against
     *                     (typically {@code master}); separate from the realm
     *                     being managed (passed per-call to KeycloakUserService).
     * @param clientId     {@code admin-cli} for password grant,
     *                     a dedicated client id for service-account grant.
     * @param clientSecret only set for service-account grant.
     * @param username     only set for password grant.
     * @param password     only set for password grant.
     */
    public record Admin(
            String serverUrl,
            String realm,
            String clientId,
            String clientSecret,
            String username,
            String password) {

        public Admin {
            if (serverUrl == null || serverUrl.isBlank()) serverUrl = "http://localhost:8180";
            if (realm     == null || realm.isBlank())     realm     = "master";
            if (clientId  == null || clientId.isBlank())  clientId  = "admin-cli";
        }

        /** True iff we have a non-empty client secret (= service-account grant). */
        public boolean isServiceAccount() {
            return clientSecret != null && !clientSecret.isBlank();
        }
    }
}
