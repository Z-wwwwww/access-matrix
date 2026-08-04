package com.platform.system.auth.controller;

import com.platform.core.infrastructure.audit.OpLogRecord;
import com.platform.core.infrastructure.audit.OpLogSink;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.security.ClientIpResolver;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.PasswordResetTokenEntity;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.entity.UserInviteEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.auth.service.PasswordResetTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two PRE-AUTH endpoints that set a user's permanent password must leave an
 * audit row. Every other credential-changing path in the project already does
 * ({@code auth.breakGlassSet}, both {@code reset-password} consoles,
 * {@code auth.unlock}, {@code auth.forceLogout}) — these two were the only ones
 * writing a credential with no trace at all, and the reset one additionally
 * detaches the Keycloak identity irreversibly.
 *
 * <p>The row must be built from the CONSUMED TOKEN, not from
 * {@code RequestContext}: there is no session on these calls, so the aspect-based
 * {@code @OpLog} would record a null user and whatever {@code X-Tenant-Id} the
 * caller chose to send — a misattributed audit row, which is worse than none.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreAuthCredentialAuditTest {

    @Mock OpLogSink opLogSink;
    @Mock ClientIpResolver clientIpResolver;
    @Mock PasswordPolicyService passwordPolicy;
    @Mock AppMailProperties mailProps;
    @Mock HttpServletRequest http;

    private void stubHttp() {
        when(http.getRequestURI()).thenReturn("/api/auth/x/TOKEN");
        when(http.getMethod()).thenReturn("POST");
        when(http.getHeader("User-Agent")).thenReturn("UA/1.0");
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.9");
        when(mailProps.baseUrl()).thenReturn("https://app.test");
    }

    private OpLogRecord captured() {
        ArgumentCaptor<OpLogRecord> cap = ArgumentCaptor.forClass(OpLogRecord.class);
        verify(opLogSink).record(cap.capture());
        return cap.getValue();
    }

    // ── invite acceptance ───────────────────────────────────────────────────

    @Test
    void inviteAccept_writesAnAuditRowAttributedToTheTokensTenantAndUser() {
        stubHttp();
        InviteTokenService tokens = mock(InviteTokenService.class);
        KeycloakUserService kc = mock(KeycloakUserService.class);

        UserInviteEntity row = new UserInviteEntity();
        row.setTenantId("acme");
        row.setUserId("ULID-USER");
        row.setKeycloakId("kc-uuid");
        when(tokens.consume("TOKEN")).thenReturn(row);

        new InviteController(tokens, kc, passwordPolicy, mailProps, opLogSink, clientIpResolver)
                .accept("TOKEN", new InviteController.AcceptInviteRequest("Str0ng!pass"), http);

        OpLogRecord r = captured();
        assertThat(r.tenantId()).as("from the token, not X-Tenant-Id").isEqualTo("acme");
        assertThat(r.userId()).isEqualTo("ULID-USER");
        assertThat(r.module()).isEqualTo("system");
        assertThat(r.action()).isEqualTo("auth.inviteAccept");
        assertThat(r.targetType()).isEqualTo("user");
        assertThat(r.targetId()).isEqualTo("ULID-USER");
        assertThat(r.clientIp()).isEqualTo("203.0.113.9");
        assertThat(r.success()).isTrue();
        // The new password must never reach the audit row.
        assertThat(r.requestBody()).isNull();
    }

    // ── SSO → password reset acceptance ─────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void passwordResetAccept_writesAnAuditRowAttributedToTheTokensTenantAndUser() {
        stubHttp();
        PasswordResetTokenService tokens = mock(PasswordResetTokenService.class);
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ObjectProvider<KeycloakUserService> kcProvider =
                (ObjectProvider<KeycloakUserService>) mock(ObjectProvider.class);
        when(kcProvider.getIfAvailable()).thenReturn(null);
        when(encoder.encode(anyString())).thenReturn("$2a$hash");

        PasswordResetTokenEntity row = new PasswordResetTokenEntity();
        row.setTenantId("acme");
        row.setUserId("ULID-USER");
        when(tokens.consume("TOKEN")).thenReturn(row);

        UserEntity user = new UserEntity();
        user.setId("ULID-USER");
        user.setTenantId("acme");
        user.setUsername("alice");
        user.setMark(1);
        when(userMapper.findByIdAndTenant("ULID-USER", "acme")).thenReturn(user);

        new PasswordResetController(tokens, userMapper, encoder, passwordPolicy,
                kcProvider, mailProps, opLogSink, clientIpResolver)
                .accept("TOKEN", new PasswordResetController.ResetPasswordRequest("Str0ng!pass"), http);

        OpLogRecord r = captured();
        assertThat(r.tenantId()).isEqualTo("acme");
        assertThat(r.userId()).isEqualTo("ULID-USER");
        assertThat(r.username()).as("resolved from the user row, not the request").isEqualTo("alice");
        assertThat(r.module()).isEqualTo("system");
        assertThat(r.action()).isEqualTo("auth.passwordResetAccept");
        assertThat(r.targetId()).isEqualTo("ULID-USER");
        assertThat(r.success()).isTrue();
        assertThat(r.requestBody()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void auditFailureNeverBreaksTheUsersReset() {
        stubHttp();
        // A misconfigured sink must not turn a successful password set into a 500.
        org.mockito.Mockito.doThrow(new IllegalStateException("sink down"))
                .when(opLogSink).record(any());

        PasswordResetTokenService tokens = mock(PasswordResetTokenService.class);
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ObjectProvider<KeycloakUserService> kcProvider =
                (ObjectProvider<KeycloakUserService>) mock(ObjectProvider.class);
        when(kcProvider.getIfAvailable()).thenReturn(null);
        when(encoder.encode(anyString())).thenReturn("$2a$hash");

        PasswordResetTokenEntity row = new PasswordResetTokenEntity();
        row.setTenantId("acme");
        row.setUserId("ULID-USER");
        when(tokens.consume("TOKEN")).thenReturn(row);

        UserEntity user = new UserEntity();
        user.setId("ULID-USER");
        user.setTenantId("acme");
        user.setUsername("alice");
        when(userMapper.findByIdAndTenant("ULID-USER", "acme")).thenReturn(user);

        var resp = new PasswordResetController(tokens, userMapper, encoder, passwordPolicy,
                kcProvider, mailProps, opLogSink, clientIpResolver)
                .accept("TOKEN", new PasswordResetController.ResetPasswordRequest("Str0ng!pass"), http);

        assertThat(resp.data()).containsKey("loginUrl");
    }
}
