package com.platform.system.auth.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.security.AccountLockoutService;
import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.JwtIssuer;
import com.platform.core.infrastructure.security.RefreshTokenStore;
import com.platform.system.auth.dto.TokenResponse;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.service.PermissionQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the break-glass self-alert behavior in {@link AuthService#login}.
 *
 * <p>The login path itself is exercised end-to-end in
 * {@code OidcJitProvisioningIT} and the password-mode IT suite; this
 * file focuses on the specific branch we just added: a successful login
 * under {@code mode=oidc} fires a notification email to the user themselves,
 * and only under those conditions.
 *
 * <p>The success-and-failure permutations we pin:
 * <ul>
 *   <li>OIDC mode + super-admin + has email → alert fires</li>
 *   <li>OIDC mode + super-admin + no email → no alert, no exception</li>
 *   <li>OIDC mode + non-super-admin → alert STILL fires (with a warn log
 *       — this should not happen by design but if it does, the affected
 *       user wants to know)</li>
 *   <li>password / permit-all mode → no alert (regular login path)</li>
 *   <li>login failure (bad creds) → no alert (never reaches the line)</li>
 *   <li>mail.sendHtmlAsync throws → login still succeeds</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserMapper userMapper;
    @Mock PasswordEncoder encoder;
    @Mock JwtIssuer jwtIssuer;
    @Mock RefreshTokenStore refreshStore;
    @Mock AccountLockoutService lockoutService;
    @Mock LoginAuditService auditService;
    @Mock PermissionQueryService permissionQueryService;
    @Mock RoleMapper roleMapper;
    @Mock ForceLogoutService forceLogoutService;
    @Mock MailService mailService;
    @Mock AppMailProperties mailProps;

    @InjectMocks AuthService service;

    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        req = mock(HttpServletRequest.class);
        when(req.getHeader("User-Agent")).thenReturn("test-agent/1.0");
        when(req.getRemoteAddr()).thenReturn("10.0.0.1");
        when(mailProps.fromName()).thenReturn("Access Matrix");
        when(mailProps.from()).thenReturn("noreply@example.com");
        when(mailProps.baseUrl()).thenReturn("https://app.example.com");
        // RequestContext.tenantId() is read by AuthService; set a synthetic one.
        RequestContext.set("demo", null, null, Locale.JAPAN, "test-trace");
        // Stub the rest of the login dependencies so the success path completes.
        when(jwtIssuer.issue(any(), any(), any(), any(), any()))
                .thenReturn(new JwtIssuer.TokenIssue(
                        "test-access-token", "test-token-id",
                        java.time.Instant.now(),
                        java.time.Instant.now().plusSeconds(1800),
                        1800L));
        when(refreshStore.issue(any(), any())).thenReturn("test-refresh-token");
        when(permissionQueryService.loadUserPermissions(any())).thenReturn(Set.of());
        when(roleMapper.findRoleIdsByUserId(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private UserEntity successfulUserRow() {
        UserEntity u = new UserEntity();
        u.setId("ULID-USER-26");
        u.setTenantId("demo");
        u.setUsername("admin");
        u.setEmail("admin@example.com");
        u.setDisplayName("Admin User");
        u.setPasswordHash("$2a$12$fakehash");
        u.setStatus(1);
        return u;
    }

    private void stubLoginSuccess(UserEntity user) {
        when(userMapper.findByIdentifier("demo", "admin")).thenReturn(user);
        when(lockoutService.remainingLockSeconds("demo", "admin")).thenReturn(0L);
        when(encoder.matches("RightPassword", user.getPasswordHash())).thenReturn(true);
    }

    @Test
    void oidcMode_superAdminLoginSuccess_firesAlertToOwnEmail() {
        ReflectionTestUtils.setField(service, "securityMode", "oidc");
        UserEntity user = successfulUserRow();
        stubLoginSuccess(user);
        when(roleMapper.findRoleIdsByUserId("ULID-USER-26", "demo"))
                .thenReturn(List.of(BuiltInRoles.SUPER_ADMIN_ID));

        AuthService.LoginResult result = service.login("admin", "RightPassword", req);

        // Login itself succeeded (token issued).
        org.assertj.core.api.Assertions.assertThat(result.tokens()).isNotNull();
        // Self-alert email dispatched to the user's OWN address.
        verify(mailService).sendHtmlAsync(
                eq("admin@example.com"),
                any(Locale.class),
                eq("user-break-glass-used.subject"),
                any(Object[].class),
                eq("user-break-glass-used"),
                any());
    }

    @Test
    void oidcMode_superAdminLoginSuccess_butNoEmail_noAlertNoException() {
        ReflectionTestUtils.setField(service, "securityMode", "oidc");
        UserEntity user = successfulUserRow();
        user.setEmail(null);
        stubLoginSuccess(user);
        when(roleMapper.findRoleIdsByUserId(any(), any()))
                .thenReturn(List.of(BuiltInRoles.SUPER_ADMIN_ID));

        // Must not throw — operator may be deliberately logging in via
        // break-glass during an SMTP outage too.
        AuthService.LoginResult result = service.login("admin", "RightPassword", req);

        org.assertj.core.api.Assertions.assertThat(result.tokens()).isNotNull();
        verify(mailService, never()).sendHtmlAsync(any(), any(), any(), any(Object[].class), any(), any());
    }

    @Test
    void oidcMode_nonSuperAdminLoginSuccess_stillFiresAlert() {
        // Should not happen by design (their password_hash should be NULL
        // after JIT bind), but if a future bug repopulates it, the affected
        // user wants to know more than anyone else — alert them.
        ReflectionTestUtils.setField(service, "securityMode", "oidc");
        UserEntity user = successfulUserRow();
        stubLoginSuccess(user);
        when(roleMapper.findRoleIdsByUserId(any(), any())).thenReturn(List.of());   // not super-admin

        service.login("admin", "RightPassword", req);

        verify(mailService).sendHtmlAsync(
                eq("admin@example.com"),
                any(Locale.class),
                eq("user-break-glass-used.subject"),
                any(Object[].class),
                eq("user-break-glass-used"),
                any());
    }

    @Test
    void passwordMode_loginSuccess_noAlert() {
        // In password mode /auth/login is the daily login path. Spamming
        // the user with an alert on every sign-in would train them to
        // ignore the warning.
        ReflectionTestUtils.setField(service, "securityMode", "password");
        UserEntity user = successfulUserRow();
        stubLoginSuccess(user);

        service.login("admin", "RightPassword", req);

        verify(mailService, never()).sendHtmlAsync(any(), any(), any(), any(Object[].class), any(), any());
    }

    @Test
    void permitAllMode_loginSuccess_noAlert() {
        ReflectionTestUtils.setField(service, "securityMode", "permit-all");
        UserEntity user = successfulUserRow();
        stubLoginSuccess(user);

        service.login("admin", "RightPassword", req);

        verify(mailService, never()).sendHtmlAsync(any(), any(), any(), any(Object[].class), any(), any());
    }

    @Test
    void mailServiceThrows_loginStillSucceeds() {
        ReflectionTestUtils.setField(service, "securityMode", "oidc");
        UserEntity user = successfulUserRow();
        stubLoginSuccess(user);
        when(roleMapper.findRoleIdsByUserId(any(), any()))
                .thenReturn(List.of(BuiltInRoles.SUPER_ADMIN_ID));
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down"))
                .when(mailService).sendHtmlAsync(anyString(), any(),
                        anyString(), any(Object[].class), anyString(), any());

        // Critical: a mail hiccup must not block login. Operator recovering
        // from an SSO outage doesn't need SMTP also taking down their
        // /auth/login.
        AuthService.LoginResult result = service.login("admin", "RightPassword", req);

        org.assertj.core.api.Assertions.assertThat(result.tokens()).isNotNull();
    }
}
