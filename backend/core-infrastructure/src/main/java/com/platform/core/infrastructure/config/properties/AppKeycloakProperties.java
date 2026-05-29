package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Server-side Keycloak Admin REST settings.
 *
 * <h3>One runtime path</h3>
 * The backend authenticates to Keycloak for ALL Admin REST work as a dedicated
 * <b>service-account client</b> using the {@code client_credentials} grant —
 * see {@link Admin}. There is no username/password path at runtime; dev and
 * prod use the exact same mechanism.
 *
 * <h3>Bootstrap</h3>
 * That provisioner client is itself created at startup by
 * {@code KeycloakProvisionerSeeder} using a throwaway admin credential — see
 * {@link Bootstrap}. This is the only place a username/password appears, it is
 * used once to create the client + grant its roles, and it can be removed after
 * the first successful boot.
 *
 * @param admin     runtime service-account identity ({@code app.keycloak.admin})
 * @param bootstrap one-time provisioner-client bootstrap ({@code app.keycloak.bootstrap})
 */
@ConfigurationProperties(prefix = "app.keycloak")
public record AppKeycloakProperties(Admin admin, Bootstrap bootstrap) {

    public AppKeycloakProperties {
        if (admin == null) admin = new Admin(null, null, null, null);
        if (bootstrap == null) bootstrap = new Bootstrap(false, null, null, null, null);
    }

    /**
     * Runtime identity: the provisioner service-account client. The backend
     * always authenticates with {@code client_credentials} using
     * {@code clientId} + {@code clientSecret}. No username/password.
     *
     * @param serverUrl    e.g. {@code http://localhost:8180}
     * @param realm        realm the client authenticates against (typically
     *                     {@code master}; separate from the realm being managed,
     *                     which is passed per-call).
     * @param clientId     the provisioner client id (default
     *                     {@code access-matrix-provisioner}).
     * @param clientSecret the provisioner client secret (client_credentials).
     */
    public record Admin(
            String serverUrl,
            String realm,
            String clientId,
            String clientSecret) {

        public Admin {
            if (serverUrl == null || serverUrl.isBlank()) serverUrl = "http://localhost:8180";
            if (realm     == null || realm.isBlank())     realm     = "master";
            if (clientId  == null || clientId.isBlank())  clientId  = "access-matrix-provisioner";
        }
    }

    /**
     * One-time bootstrap of the {@link Admin} provisioner client. Uses a
     * throwaway admin credential (dev: Keycloak's own {@code admin/admin}; prod:
     * a one-time root, removable after first boot) to ensure the provisioner
     * client exists with {@code create-realm} plus {@code manage-users} on each
     * pre-existing managed realm (which the create-realm auto-grant does NOT
     * cover, since the provisioner did not create them).
     *
     * <p>Set {@code enabled=false} once the client exists to skip this entirely
     * (and drop the bootstrap credential).
     *
     * @param enabled       run the bootstrap seeder at startup
     * @param clientId      public client used for the password grant (default
     *                      {@code admin-cli})
     * @param username      bootstrap admin username (authenticates against
     *                      {@link Admin#realm()})
     * @param password      bootstrap admin password
     * @param managedRealms pre-existing realms to grant the provisioner
     *                      manage-users on (e.g. {@code demo}, {@code system}).
     *                      Realms the provisioner later creates itself are
     *                      auto-granted by Keycloak and need not be listed.
     */
    public record Bootstrap(
            boolean enabled,
            String clientId,
            String username,
            String password,
            List<String> managedRealms) {

        public Bootstrap {
            if (clientId == null || clientId.isBlank()) clientId = "admin-cli";
            if (managedRealms == null) managedRealms = List.of();
        }
    }
}
