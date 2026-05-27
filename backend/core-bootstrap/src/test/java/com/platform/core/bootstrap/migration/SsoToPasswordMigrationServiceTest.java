package com.platform.core.bootstrap.migration;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.PasswordResetTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Per-branch coverage for the reverse migration. Service mints reset
 * tokens and dispatches them to MailService — both are mocked so the
 * test asserts ON the bucket categorization and the side-effect
 * sequencing without needing a DB or SMTP server.
 *
 * <p>The full end-to-end smoke (real PG + mail catcher + frontend reset
 * accept flow) is intentionally out of scope here; this test pins
 * branching semantics, the IT pins integration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SsoToPasswordMigrationServiceTest {

    @Mock UserMapper userMapper;
    @Mock PasswordResetTokenService tokens;
    @Mock MailService mailService;
    @Mock AppMailProperties mailProps;

    @InjectMocks SsoToPasswordMigrationService service;

    private List<UserEntity> dbUsers;

    @BeforeEach
    void freshDb() {
        dbUsers = new ArrayList<>();
        when(userMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> dbUsers);
        when(mailProps.fromName()).thenReturn("Access Matrix");
        when(mailProps.from()).thenReturn("noreply@example.com");
        when(mailProps.baseUrl()).thenReturn("https://app.example.com");
    }

    private UserEntity row(String id, String username, String email, String kcId) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setDisplayName(username);
        u.setKeycloakId(kcId);
        return u;
    }

    @Test
    void happyPath_mintsTokenAndSendsResetEmail() {
        dbUsers.add(row("ULID-A", "alice", "alice@example.com", "kc-uuid-A"));
        when(tokens.mint("demo", "ULID-A", "kc-uuid-A")).thenReturn("CLEARTEXT_TOKEN");

        MigrationReport report = service.run(List.of("demo"));

        verify(tokens).mint("demo", "ULID-A", "kc-uuid-A");
        verify(mailService).sendHtmlAsync(
                eq("alice@example.com"),
                eq(Locale.JAPAN),
                eq("user-password-reset.subject"),
                any(Object[].class),
                eq("user-password-reset"),
                any());
        assertThat(report.tenants).hasSize(1);
        assertThat(report.tenants.get(0).created).hasSize(1);
        MigrationReport.Created c = report.tenants.get(0).created.get(0);
        assertThat(c.userId).isEqualTo("ULID-A");
        assertThat(c.keycloakId).isEqualTo("kc-uuid-A");
        assertThat(c.emailSent).isTrue();
    }

    @Test
    void skipsUserMissingEmail() {
        dbUsers.add(row("ULID-B", "bob", null, "kc-uuid-B"));

        MigrationReport report = service.run(List.of("demo"));

        verify(tokens, never()).mint(anyString(), anyString(), anyString());
        verify(mailService, never()).sendHtmlAsync(
                anyString(), any(), anyString(), any(Object[].class), anyString(), any());
        assertThat(report.tenants.get(0).skipped).hasSize(1);
        assertThat(report.tenants.get(0).skipped.get(0).reason).isEqualTo("missing-email");
    }

    @Test
    void skipsUserMissingUsername() {
        dbUsers.add(row("ULID-C", null, "carol@example.com", "kc-uuid-C"));

        MigrationReport report = service.run(List.of("demo"));

        verify(tokens, never()).mint(anyString(), anyString(), anyString());
        assertThat(report.tenants.get(0).skipped).hasSize(1);
        assertThat(report.tenants.get(0).skipped.get(0).reason).isEqualTo("missing-username");
    }

    @Test
    void recordsFailureWhenTokenMintThrows_andContinuesWithNextUser() {
        dbUsers.add(row("ULID-D", "dave", "dave@example.com", "kc-uuid-D"));
        dbUsers.add(row("ULID-E", "erin", "erin@example.com", "kc-uuid-E"));
        when(tokens.mint(anyString(), eq("ULID-D"), anyString()))
                .thenThrow(new RuntimeException("DB write failed"));
        when(tokens.mint(anyString(), eq("ULID-E"), anyString())).thenReturn("OK_TOKEN");

        MigrationReport report = service.run(List.of("demo"));

        assertThat(report.tenants.get(0).failed).hasSize(1);
        assertThat(report.tenants.get(0).failed.get(0).stage).isEqualTo("mint-token");
        assertThat(report.tenants.get(0).failed.get(0).username).isEqualTo("dave");
        // Batch continues — erin still gets a token + email.
        assertThat(report.tenants.get(0).created).hasSize(1);
        assertThat(report.tenants.get(0).created.get(0).username).isEqualTo("erin");
    }

    @Test
    void recordsFailureWhenMailThrows_andDoesNotRollbackToken() {
        dbUsers.add(row("ULID-F", "frank", "frank@example.com", "kc-uuid-F"));
        when(tokens.mint("demo", "ULID-F", "kc-uuid-F")).thenReturn("TOK");
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down"))
                .when(mailService).sendHtmlAsync(
                        anyString(), any(), anyString(), any(Object[].class), anyString(), any());

        MigrationReport report = service.run(List.of("demo"));

        // Token was minted (still in DB, will expire on its own). Operator
        // can re-run after fixing SMTP and a fresh token will be issued.
        verify(tokens).mint("demo", "ULID-F", "kc-uuid-F");
        assertThat(report.tenants.get(0).failed).hasSize(1);
        assertThat(report.tenants.get(0).failed.get(0).stage).isEqualTo("send-reset-email");
        assertThat(report.tenants.get(0).created).isEmpty();
    }

    @Test
    void iteratesMultipleTenantsSequentially() {
        dbUsers.add(row("ULID-G", "gina", "gina@example.com", "kc-uuid-G"));
        when(tokens.mint(anyString(), anyString(), anyString())).thenReturn("T");

        MigrationReport report = service.run(List.of("demo", "acme"));

        assertThat(report.tenants).hasSize(2);
        assertThat(report.tenants.get(0).tenantId).isEqualTo("demo");
        assertThat(report.tenants.get(1).tenantId).isEqualTo("acme");
        verify(tokens, org.mockito.Mockito.times(2))
                .mint(anyString(), eq("ULID-G"), anyString());
    }

    @Test
    void skipsBlankOrNullTenantId() {
        MigrationReport report = service.run(java.util.Arrays.asList("demo", null, "", "  "));

        assertThat(report.tenants).hasSize(1);
        assertThat(report.tenants.get(0).tenantId).isEqualTo("demo");
    }
}
