package com.platform.core.infrastructure.security.keycloak;

import com.platform.core.infrastructure.config.properties.AppKeycloakProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit coverage for the cheap-but-important kill switch:
 * {@code app.keycloak.bootstrap.enabled=false} must make the seeder a complete
 * no-op — it must not even open a bootstrap admin connection. (The create /
 * grant path is deep Keycloak Admin REST orchestration and is verified by a
 * live-Keycloak smoke test, not mocks.)
 *
 * <p>Lives in core-system because core-infrastructure has no test harness; the
 * class under test is public and reachable via the module dependency.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakProvisionerSeederTest {

    @Mock KeycloakAdminClientFactory factory;

    private AppKeycloakProperties props(boolean bootstrapEnabled) {
        return new AppKeycloakProperties(
                new AppKeycloakProperties.Admin("http://kc:8180", "master",
                        "access-matrix-provisioner", "secret"),
                new AppKeycloakProperties.Bootstrap(bootstrapEnabled, "admin-cli",
                        "admin", "admin", List.of("demo", "system")));
    }

    @Test
    void bootstrapDisabled_isCompleteNoOp() {
        KeycloakProvisionerSeeder seeder = new KeycloakProvisionerSeeder(props(false), factory);

        seeder.seed();

        // Must not even build a bootstrap client when disabled.
        verifyNoInteractions(factory);
    }

    @Test
    void bootstrapEnabled_opensBootstrapClientNotRuntime() {
        // factory.bootstrapClient() returns null here → seed() will NPE inside
        // and fall into its fail-soft catch; we only assert it ATTEMPTED the
        // bootstrap connection (didn't skip) and never used the runtime client.
        KeycloakProvisionerSeeder seeder = new KeycloakProvisionerSeeder(props(true), factory);

        seeder.seed();

        verify(factory).bootstrapClient();
        verify(factory, never()).runtimeClient();
    }
}
