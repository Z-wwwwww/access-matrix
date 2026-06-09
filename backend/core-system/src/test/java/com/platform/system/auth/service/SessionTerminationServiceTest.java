package com.platform.system.auth.service;

import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the bug-1 fix: {@code terminateUser} must not only kick the access token
 * but ALSO end the Keycloak SSO session — otherwise a kicked user is silently
 * re-authenticated on the /login redirect by the still-live KC session.
 */
@ExtendWith(MockitoExtension.class)
class SessionTerminationServiceTest {

    @Mock ForceLogoutService forceLogout;
    @Mock KeycloakUserService kc;
    @Mock JdbcTemplate jdbc;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<KeycloakUserService> kcProvider = mock(ObjectProvider.class);

    private SessionTerminationService service;

    @BeforeEach
    void setUp() {
        service = new SessionTerminationService(forceLogout, kcProvider, jdbc);
    }

    @Test
    void terminateUser_kicksTokenAndEndsKeycloakSession() {
        when(kcProvider.getIfAvailable()).thenReturn(kc);
        when(jdbc.queryForMap(anyString(), eq("u1")))
                .thenReturn(Map.of("tenant_id", "acme", "keycloak_id", "kc-uuid-1"));

        service.terminateUser("u1");

        verify(forceLogout).kickOut("u1");
        verify(kc).logoutUser("acme", "kc-uuid-1");   // ← the bug-1 fix
    }

    @Test
    void terminateUser_nonOidc_justKicks() {
        when(kcProvider.getIfAvailable()).thenReturn(null);   // non-oidc mode

        service.terminateUser("u1");

        verify(forceLogout).kickOut("u1");
        verify(jdbc, never()).queryForMap(anyString(), eq("u1"));
    }

    @Test
    void terminateUser_userRowGone_stillKicks_noLogout() {
        when(kcProvider.getIfAvailable()).thenReturn(kc);
        when(jdbc.queryForMap(anyString(), eq("u1")))
                .thenThrow(new EmptyResultDataAccessException(1));

        service.terminateUser("u1");

        verify(forceLogout).kickOut("u1");
        verify(kc, never()).logoutUser(anyString(), anyString());
    }

    @Test
    void terminateTenant_andReactivate_delegateToForceLogout() {
        service.terminateTenant("acme");
        verify(forceLogout).kickOutTenant("acme");

        service.reactivateTenant("acme");
        verify(forceLogout).clearTenant("acme");
    }
}
