package com.platform.core.bootstrap.migration;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the per-user branching that {@link PasswordToSsoMigrationService}
 * has to keep straight. The service has one happy path (create + email)
 * and four skip/fail branches that all need to land in their bucket and
 * NOT cascade into the next user.
 *
 * <p>No Spring context — pure Mockito over UserMapper + KeycloakUserService.
 * The full end-to-end smoke (real KC + PG containers, real OIDC token + JIT
 * bind on login) lives in {@code PasswordToSsoMigrationIT}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordToSsoMigrationServiceTest {

    @Mock UserMapper userMapper;
    @Mock KeycloakUserService keycloak;
    @InjectMocks PasswordToSsoMigrationService service;

    private List<UserEntity> dbUsers;

    @BeforeEach
    void freshDb() {
        dbUsers = new ArrayList<>();
        when(userMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> dbUsers);
    }

    private UserEntity row(String id, String username, String email) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setDisplayName(username);
        return u;
    }

    @Test
    void happyPath_createsKcUserAndTriggersResetEmail() {
        dbUsers.add(row("ULID-A", "alice", "alice@example.com"));
        when(keycloak.findUserIdByUsername("demo", "alice")).thenReturn(null);
        when(keycloak.createUser("demo", "alice", "alice@example.com", "alice", null))
                .thenReturn("kc-uuid-alice");

        MigrationReport report = service.run(List.of("demo"));

        verify(keycloak).createUser("demo", "alice", "alice@example.com", "alice", null);
        verify(keycloak).executeActionsEmail("demo", "kc-uuid-alice", List.of("UPDATE_PASSWORD"));

        assertThat(report.tenants).hasSize(1);
        assertThat(report.tenants.get(0).created).hasSize(1);
        assertThat(report.tenants.get(0).skipped).isEmpty();
        assertThat(report.tenants.get(0).failed).isEmpty();
        MigrationReport.Created c = report.tenants.get(0).created.get(0);
        assertThat(c.userId).isEqualTo("ULID-A");
        assertThat(c.keycloakId).isEqualTo("kc-uuid-alice");
        assertThat(c.emailSent).isTrue();
    }

    @Test
    void skipsUserMissingUsername() {
        dbUsers.add(row("ULID-B", null, "bob@example.com"));

        MigrationReport report = service.run(List.of("demo"));

        verify(keycloak, never()).createUser(any(), any(), any(), any(), any());
        assertThat(report.tenants.get(0).skipped).hasSize(1);
        assertThat(report.tenants.get(0).skipped.get(0).reason).isEqualTo("missing-username");
    }

    @Test
    void skipsUserMissingEmail() {
        dbUsers.add(row("ULID-C", "carol", null));

        MigrationReport report = service.run(List.of("demo"));

        verify(keycloak, never()).createUser(any(), any(), any(), any(), any());
        assertThat(report.tenants.get(0).skipped).hasSize(1);
        assertThat(report.tenants.get(0).skipped.get(0).reason).isEqualTo("missing-email");
    }

    @Test
    void idempotent_skipsUserAlreadyInKeycloak() {
        // A partial earlier run left "dave" in KC — re-run must NOT recreate
        // and must NOT re-email (would spam users on every restart).
        dbUsers.add(row("ULID-D", "dave", "dave@example.com"));
        when(keycloak.findUserIdByUsername("demo", "dave")).thenReturn("kc-uuid-existing-dave");

        MigrationReport report = service.run(List.of("demo"));

        verify(keycloak, never()).createUser(any(), any(), any(), any(), any());
        verify(keycloak, never()).executeActionsEmail(any(), any(), anyList());
        assertThat(report.tenants.get(0).created).isEmpty();
        assertThat(report.tenants.get(0).skipped).hasSize(1);
        assertThat(report.tenants.get(0).skipped.get(0).reason).isEqualTo("kc-user-already-exists");
    }

    @Test
    void recordsFailureWhenCreateUserThrows_andContinuesWithNextUser() {
        dbUsers.add(row("ULID-E", "erin", "erin@example.com"));
        dbUsers.add(row("ULID-F", "frank", "frank@example.com"));
        when(keycloak.findUserIdByUsername(anyString(), anyString())).thenReturn(null);
        when(keycloak.createUser(eq("demo"), eq("erin"), any(), any(), any()))
                .thenThrow(new RuntimeException("KC unreachable"));
        when(keycloak.createUser(eq("demo"), eq("frank"), any(), any(), any()))
                .thenReturn("kc-uuid-frank");

        MigrationReport report = service.run(List.of("demo"));

        // erin failed at create, frank succeeded — the batch did not abort.
        assertThat(report.tenants.get(0).failed).hasSize(1);
        assertThat(report.tenants.get(0).failed.get(0).username).isEqualTo("erin");
        assertThat(report.tenants.get(0).failed.get(0).stage).isEqualTo("create-kc-user");
        assertThat(report.tenants.get(0).created).hasSize(1);
        assertThat(report.tenants.get(0).created.get(0).username).isEqualTo("frank");
    }

    @Test
    void recordsFailureWhenEmailThrows_butKcUserStaysCreated() {
        dbUsers.add(row("ULID-G", "gina", "gina@example.com"));
        when(keycloak.findUserIdByUsername("demo", "gina")).thenReturn(null);
        when(keycloak.createUser("demo", "gina", "gina@example.com", "gina", null))
                .thenReturn("kc-uuid-gina");
        // Realm SMTP misconfigured — KC returns 500 on the email call.
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP unreachable"))
                .when(keycloak).executeActionsEmail("demo", "kc-uuid-gina",
                        List.of("UPDATE_PASSWORD"));

        MigrationReport report = service.run(List.of("demo"));

        // create succeeded (KC user exists, idempotent re-run will skip it),
        // but the email leg failed — operator must resend after fixing SMTP.
        verify(keycloak).createUser("demo", "gina", "gina@example.com", "gina", null);
        assertThat(report.tenants.get(0).failed).hasSize(1);
        assertThat(report.tenants.get(0).failed.get(0).stage).isEqualTo("send-reset-email");
        assertThat(report.tenants.get(0).created).isEmpty();
    }

    @Test
    void iteratesMultipleTenantsSequentially() {
        // Each tenant gets its own pass over the candidate list — the mock
        // returns the same list both times but the service runs them under
        // different synthetic RequestContexts.
        dbUsers.add(row("ULID-H", "harriet", "harriet@example.com"));
        when(keycloak.findUserIdByUsername(anyString(), eq("harriet"))).thenReturn(null);
        when(keycloak.createUser(anyString(), eq("harriet"), any(), any(), any()))
                .thenReturn("kc-uuid-harriet");

        MigrationReport report = service.run(List.of("demo", "acme", "beta"));

        assertThat(report.tenants).hasSize(3);
        assertThat(report.tenants.get(0).tenantId).isEqualTo("demo");
        assertThat(report.tenants.get(1).tenantId).isEqualTo("acme");
        assertThat(report.tenants.get(2).tenantId).isEqualTo("beta");
        verify(keycloak, times(3)).createUser(any(), eq("harriet"), any(), any(), any());
    }

    // ─── resend() ─────────────────────────────────────────────────────
    // Companion entry point for the case where users let the reset-password
    // email expire (KC's default link lifespan is 12 h). The candidate set
    // is the same as run() (keycloak_id IS NULL) but the action is different:
    // skip create, ONLY re-fire executeActionsEmail against the existing KC user.

    @Test
    void resend_emailsExistingKcUserAndSkipsCreate() {
        dbUsers.add(row("ULID-I", "ivy", "ivy@example.com"));
        when(keycloak.findUserIdByUsername("demo", "ivy")).thenReturn("kc-uuid-ivy");

        MigrationReport report = service.resend(List.of("demo"));

        // No createUser — that's the whole point of the dedicated entry point.
        verify(keycloak, never()).createUser(any(), any(), any(), any(), any());
        // Email gets re-fired against the existing KC user.
        verify(keycloak).executeActionsEmail("demo", "kc-uuid-ivy", List.of("UPDATE_PASSWORD"));
        // Bucketed under "created" (reused tally for "email re-issued").
        assertThat(report.tenants.get(0).created).hasSize(1);
        assertThat(report.tenants.get(0).created.get(0).keycloakId).isEqualTo("kc-uuid-ivy");
    }

    @Test
    void resend_skipsRowsWithNoKcUserYet() {
        // A row that was never run() through migration has no KC user.
        // resend() must NOT silently create one — that would mask a misconfig
        // (the operator forgot to run the first migration pass).
        dbUsers.add(row("ULID-J", "jen", "jen@example.com"));
        when(keycloak.findUserIdByUsername("demo", "jen")).thenReturn(null);

        MigrationReport report = service.resend(List.of("demo"));

        verify(keycloak, never()).createUser(any(), any(), any(), any(), any());
        verify(keycloak, never()).executeActionsEmail(any(), any(), anyList());
        assertThat(report.tenants.get(0).created).isEmpty();
        assertThat(report.tenants.get(0).skipped).hasSize(1);
        assertThat(report.tenants.get(0).skipped.get(0).reason)
                .isEqualTo("no-kc-user-yet-run-migration-first");
    }

    @Test
    void resend_recordsFailureIfEmailThrows() {
        dbUsers.add(row("ULID-K", "kara", "kara@example.com"));
        when(keycloak.findUserIdByUsername("demo", "kara")).thenReturn("kc-uuid-kara");
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP unreachable"))
                .when(keycloak).executeActionsEmail("demo", "kc-uuid-kara",
                        List.of("UPDATE_PASSWORD"));

        MigrationReport report = service.resend(List.of("demo"));

        assertThat(report.tenants.get(0).failed).hasSize(1);
        assertThat(report.tenants.get(0).failed.get(0).stage).isEqualTo("send-reset-email");
        assertThat(report.tenants.get(0).created).isEmpty();
    }

    @Test
    void resend_skipsUserMissingUsername() {
        dbUsers.add(row("ULID-L", null, "lara@example.com"));

        MigrationReport report = service.resend(List.of("demo"));

        verify(keycloak, never()).findUserIdByUsername(any(), any());
        verify(keycloak, never()).executeActionsEmail(any(), any(), anyList());
        assertThat(report.tenants.get(0).skipped).hasSize(1);
        assertThat(report.tenants.get(0).skipped.get(0).reason).isEqualTo("missing-username");
    }

    @Test
    void resend_doesNotTouchUsersWhoAlreadyBound() {
        // Critical safety property: once a user has bound (keycloak_id set
        // via the bind path on first SSO login), resend must skip them.
        // The candidate query already filters on `keycloak_id IS NULL`, so
        // bound users never even enter the loop. Simulate the contract by
        // returning ZERO candidates from the mapper even though we have
        // "users" in the underlying list.
        dbUsers.clear();   // mapper returns 0 → bound users absent
        // (would-be bound user just to make the intent clear)
        UserEntity bound = row("ULID-M", "mark", "mark@example.com");
        bound.setKeycloakId("kc-uuid-mark-already");
        // dbUsers stays empty — the mapper QueryWrapper isNull("keycloak_id")
        // would have filtered "mark" out.

        MigrationReport report = service.resend(List.of("demo"));

        verify(keycloak, never()).executeActionsEmail(any(), any(), anyList());
        assertThat(report.tenants.get(0).created).isEmpty();
        assertThat(report.tenants.get(0).skipped).isEmpty();
        assertThat(report.tenants.get(0).failed).isEmpty();
    }

    @Test
    void skipsBlankOrNullTenantId() {
        MigrationReport report = service.run(java.util.Arrays.asList("demo", null, "", "  "));

        // Only "demo" should have produced a tenant bucket.
        assertThat(report.tenants).hasSize(1);
        assertThat(report.tenants.get(0).tenantId).isEqualTo("demo");
    }
}
