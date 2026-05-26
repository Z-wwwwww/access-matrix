package com.platform.core.bootstrap.startup;

import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the LocalKeycloakAdminSeeder behaviours that matter:
 *
 *   1. when 'admin' already exists in the realm, the seeder is a no-op —
 *      no createUser / no setPassword call. (Don't reset a password the
 *      dev may have rotated.)
 *   2. when 'admin' is missing, the seeder calls createUser + setPassword
 *      with temporary=false so the dev default doesn't trip a password-
 *      change required-action on every fresh setup.
 *   3. Keycloak Admin REST failures are SWALLOWED — startup must not crash
 *      just because the IdP isn't reachable yet.
 */
@ExtendWith(MockitoExtension.class)
class LocalKeycloakAdminSeederTest {

    @Mock KeycloakUserService keycloakUserService;
    @InjectMocks LocalKeycloakAdminSeeder seeder;

    @Test
    void existingAdmin_isNoOp() {
        when(keycloakUserService.userExists("default", "admin")).thenReturn(true);

        seeder.seed();

        verify(keycloakUserService, never()).createUser(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(keycloakUserService, never()).setPassword(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void missingAdmin_createsThenSetsPermanentPassword() {
        when(keycloakUserService.userExists("default", "admin")).thenReturn(false);
        when(keycloakUserService.createUser(
                eq("default"), eq("admin"), eq("admin@platform.local"), eq("Local Admin"), eq("admin")))
                .thenReturn("kc-uuid-admin");

        seeder.seed();

        verify(keycloakUserService).createUser(
                eq("default"), eq("admin"), eq("admin@platform.local"), eq("Local Admin"), eq("admin"));
        // Must be temporary=false — otherwise admin/admin triggers
        // UPDATE_PASSWORD required action on first login, breaking the
        // "fresh setup → log in as admin/admin" promise.
        verify(keycloakUserService).setPassword(eq("default"), eq("kc-uuid-admin"), eq("admin"), eq(false));
    }

    @Test
    void keycloakUnreachable_doesNotCrashStartup() {
        // Simulate Keycloak down — startup hook must swallow + log, not throw.
        when(keycloakUserService.userExists(anyString(), anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        // Should not throw.
        seeder.seed();

        verify(keycloakUserService, never()).createUser(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /** Inline import so the test compiles without an extra static import line. */
    private static boolean anyBoolean() { return org.mockito.ArgumentMatchers.anyBoolean(); }
}
