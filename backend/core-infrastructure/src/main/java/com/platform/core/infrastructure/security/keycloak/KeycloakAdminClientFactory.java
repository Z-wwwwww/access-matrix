package com.platform.core.infrastructure.security.keycloak;

import com.platform.core.infrastructure.config.properties.AppKeycloakProperties;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Builds Keycloak Admin REST clients. Two intentionally-distinct flavours:
 *
 * <ul>
 *   <li>{@link #runtimeClient()} — the ONLY path the backend uses for real
 *       work: {@code client_credentials} as the provisioner service account.
 *       No username/password anywhere; identical in dev and prod.</li>
 *   <li>{@link #bootstrapClient()} — a password-grant client used SOLELY by
 *       {@link KeycloakProvisionerSeeder} to create the provisioner client on
 *       first boot. Never used for runtime operations.</li>
 * </ul>
 *
 * <p>Only loaded under {@code app.security.mode=oidc}, matching the Keycloak
 * service beans.
 */
@Component
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class KeycloakAdminClientFactory {

    private final AppKeycloakProperties props;

    public KeycloakAdminClientFactory(AppKeycloakProperties props) {
        this.props = props;
    }

    /**
     * Runtime admin client — {@code client_credentials} as the provisioner.
     * Callers must close it (it owns a Resteasy client); use try-with-resources.
     */
    public Keycloak runtimeClient() {
        AppKeycloakProperties.Admin a = props.admin();
        return KeycloakBuilder.builder()
                .serverUrl(a.serverUrl())
                .realm(a.realm())
                .clientId(a.clientId())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientSecret(a.clientSecret())
                .build();
    }

    /**
     * One-time bootstrap client — password grant with the throwaway admin.
     * Used only to create/repair the provisioner client. Caller must close it.
     */
    public Keycloak bootstrapClient() {
        AppKeycloakProperties.Admin a = props.admin();
        AppKeycloakProperties.Bootstrap b = props.bootstrap();
        return KeycloakBuilder.builder()
                .serverUrl(a.serverUrl())
                .realm(a.realm())
                .clientId(b.clientId())
                .grantType(OAuth2Constants.PASSWORD)
                .username(b.username())
                .password(b.password())
                .build();
    }
}
