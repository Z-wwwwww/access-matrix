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
 * Same shape as {@link LocalKeycloakAdminSeederTest}, applied to the
 * system realm. Pins:
 *
 *   1. when 'ops' exists in the 'system' realm → no-op
 *   2. when 'ops' is missing → createUser + setPassword(temporary=false)
 *   3. Keycloak unavailable → swallow, don't crash startup
 *
 * The realm name 'system' is hardcoded in the seeder; if anything ever
 * needs to make it configurable, update both the seeder and this test.
 */
@ExtendWith(MockitoExtension.class)
class SystemKeycloakAdminSeederTest {

    @Mock KeycloakUserService keycloakUserService;
    @InjectMocks SystemKeycloakAdminSeeder seeder;

    @Test
    void existingOps_isNoOp() {
        when(keycloakUserService.userExists("system", "ops")).thenReturn(true);

        seeder.seed();

        verify(keycloakUserService, never()).createUser(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(keycloakUserService, never()).setPassword(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void missingOps_createsThenSetsPermanentPassword() {
        when(keycloakUserService.userExists("system", "ops")).thenReturn(false);
        when(keycloakUserService.createUser(
                eq("system"), eq("ops"), eq("ops@platform.local"), eq("Platform Ops"), eq("ops")))
                .thenReturn("kc-uuid-ops");

        seeder.seed();

        verify(keycloakUserService).createUser(
                eq("system"), eq("ops"), eq("ops@platform.local"), eq("Platform Ops"), eq("ops"));
        // Permanent password — local dev should be able to sign in as ops/ops
        // without first being forced through an UPDATE_PASSWORD action.
        verify(keycloakUserService).setPassword(eq("system"), eq("kc-uuid-ops"), eq("ops"), eq(false));
    }

    @Test
    void keycloakUnreachable_doesNotCrashStartup() {
        when(keycloakUserService.userExists(anyString(), anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        seeder.seed();

        verify(keycloakUserService, never()).createUser(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private static boolean anyBoolean() { return org.mockito.ArgumentMatchers.anyBoolean(); }
}
