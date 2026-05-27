package com.platform.core.bootstrap.migration;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke for {@link PasswordToSsoMigrationService} against a real
 * Postgres + Keycloak stack. Verifies the contract that the unit test
 * (PasswordToSsoMigrationServiceTest) only stubs:
 *
 * <ol>
 *   <li>Seed 3 password-mode users in the DB (no keycloak_id).</li>
 *   <li>Run the migration service.</li>
 *   <li>The Keycloak realm now has those 3 users (verified via Admin API).</li>
 *   <li>The DB rows are untouched — keycloak_id is still NULL on every row
 *       (the binding happens on first SSO login via OidcJitUserService's
 *       bind path, which is exercised by {@code OidcJitProvisioningIT}).</li>
 *   <li>Re-running the migration is a no-op — all 3 land in the skipped
 *       bucket with reason "kc-user-already-exists".</li>
 * </ol>
 *
 * <p>SMTP is intentionally NOT configured on the test realm, so the email
 * leg will land each migrated user in the {@code failed} bucket with
 * stage={@code send-reset-email}. That's a deliberate test of the partial-
 * failure path: even when email delivery fails, the KC user IS created and
 * idempotent re-runs correctly skip them.
 *
 * <p>Docker-gated via {@code @Testcontainers(disabledWithoutDocker=true)} so
 * contributors without Docker still get a green build.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PasswordToSsoMigrationIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("core_migration_it")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    @SuppressWarnings("resource")
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
            .withRealmImportFile("/default-realm.json");

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",       POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username",  POSTGRES::getUsername);
        r.add("spring.datasource.password",  POSTGRES::getPassword);
        r.add("spring.data.redis.host",      REDIS::getHost);
        r.add("spring.data.redis.port",      REDIS::getFirstMappedPort);
        r.add("spring.data.redis.password",  () -> "");
        r.add("app.security.mode",                  () -> "oidc");
        r.add("app.security.oidc.issuer-base-uri",  KEYCLOAK::getAuthServerUrl);
        // Keycloak admin REST creds for the migration service to talk to.
        r.add("app.keycloak.admin.server-url",  KEYCLOAK::getAuthServerUrl);
        r.add("app.keycloak.admin.realm",       () -> "master");
        r.add("app.keycloak.admin.client-id",   () -> "admin-cli");
        r.add("app.keycloak.admin.username",    KEYCLOAK::getAdminUsername);
        r.add("app.keycloak.admin.password",    KEYCLOAK::getAdminPassword);
        r.add("app.mail.enabled", () -> "false");
        // CRITICAL: the migration runner only auto-starts on
        // app.migration.run-on-startup=password-to-sso. We DO NOT want it
        // to fire automatically here — the test invokes service.run(...)
        // directly so it can seed users first and assert on the report.
        // (The runner-level wiring is exercised by the manual deploy + the
        // service-level tests above.)
    }

    @Autowired UserMapper userMapper;
    @Autowired PasswordToSsoMigrationService migration;

    @Test
    void migrate_thenIdempotent_thenKcStateMatches() {
        // ──────────────────────────────────────────────────────────────
        // 1. Seed three password-mode users directly into core_auth_user.
        //    No keycloak_id — these are the legacy rows the migration
        //    is meant to mirror into Keycloak.
        // ──────────────────────────────────────────────────────────────
        String runId = UUID.randomUUID().toString().substring(0, 8);
        RequestContext.set("demo", "test-seed", "test-seed", Locale.JAPAN, "seed-" + runId);
        try {
            insertLegacyUser("legacy-alice-" + runId, "alice-" + runId + "@migration.local");
            insertLegacyUser("legacy-bob-"   + runId, "bob-"   + runId + "@migration.local");
            insertLegacyUser("legacy-carol-" + runId, "carol-" + runId + "@migration.local");
        } finally {
            RequestContext.clear();
        }

        // ──────────────────────────────────────────────────────────────
        // 2. Run the migration.
        // ──────────────────────────────────────────────────────────────
        MigrationReport first = migration.run(List.of("demo"));
        assertThat(first.tenants).hasSize(1);
        MigrationReport.TenantResult firstBucket = first.tenants.get(0);
        // KC user creation must succeed for all three; the email step
        // will land each in the failed bucket because the test realm has
        // no SMTP — which is exactly the partial-failure surface this IT
        // is meant to pin.
        int kcUsersTouched = firstBucket.created.size() + firstBucket.failed.size();
        assertThat(kcUsersTouched)
                .as("all 3 seeded users must have had their KC create attempted")
                .isEqualTo(3);

        // ──────────────────────────────────────────────────────────────
        // 3. KC really does have those users now (verify via Admin API).
        // ──────────────────────────────────────────────────────────────
        try (Keycloak kc = adminClient()) {
            for (String u : List.of("legacy-alice-" + runId, "legacy-bob-" + runId, "legacy-carol-" + runId)) {
                List<UserRepresentation> hits = kc.realm("demo").users().searchByUsername(u, true);
                assertThat(hits)
                        .as("KC realm should contain migrated user %s", u)
                        .isNotEmpty();
            }
        }

        // ──────────────────────────────────────────────────────────────
        // 4. DB rows untouched — keycloak_id is still NULL. The binding
        //    happens on first SSO login via OidcJitUserService.
        // ──────────────────────────────────────────────────────────────
        RequestContext.set("demo", "test-verify", "test-verify", Locale.JAPAN, "verify-" + runId);
        try {
            for (String u : List.of("legacy-alice-" + runId, "legacy-bob-" + runId, "legacy-carol-" + runId)) {
                UserEntity row = userMapper.findByIdentifier("demo", u);
                assertThat(row).as("DB row for %s", u).isNotNull();
                assertThat(row.getKeycloakId())
                        .as("DB row's keycloak_id should still be NULL after migration — bind path writes it on first SSO login, not here")
                        .isNull();
            }
        } finally {
            RequestContext.clear();
        }

        // ──────────────────────────────────────────────────────────────
        // 5. Re-running is a no-op. Idempotent skip for every user.
        // ──────────────────────────────────────────────────────────────
        MigrationReport second = migration.run(List.of("demo"));
        MigrationReport.TenantResult secondBucket = second.tenants.get(0);
        // Each previously-seeded user should now appear in the skipped
        // bucket with the kc-user-already-exists reason.
        long skippedAlreadyExists = secondBucket.skipped.stream()
                .filter(s -> "kc-user-already-exists".equals(s.reason))
                .filter(s -> s.username != null && s.username.endsWith(runId))
                .count();
        assertThat(skippedAlreadyExists)
                .as("re-run must skip the 3 users we already mirrored")
                .isEqualTo(3);
        // And we must NOT have created or failed any of them on the re-run.
        long touchedThisRun = secondBucket.created.stream()
                .filter(c -> c.username != null && c.username.endsWith(runId)).count()
                + secondBucket.failed.stream()
                .filter(f -> f.username != null && f.username.endsWith(runId)).count();
        assertThat(touchedThisRun)
                .as("re-run must NOT touch users who already live in KC")
                .isZero();
    }

    private void insertLegacyUser(String username, String email) {
        UserEntity u = new UserEntity();
        u.setId(IdGenerator.ulid());
        u.setTenantId("demo");
        u.setUsername(username);
        u.setEmail(email);
        u.setDisplayName(username);
        // password_hash carries a real-looking bcrypt so the row passes
        // the NOT NULL constraint that V2 still imposes. The actual
        // string never gets verified — break-glass would, but this IT
        // doesn't exercise the password login path.
        u.setPasswordHash("$2a$12$placeholderbcryptHashThatNeverGetsVerifiedDDD");
        u.setStatus(1);
        u.setMark(1);
        u.setCreateUser("test");
        u.setUpdateUser("test");
        u.setCreateTime(java.time.LocalDateTime.now());
        u.setUpdateTime(java.time.LocalDateTime.now());
        userMapper.insert(u);
    }

    private Keycloak adminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .build();
    }
}
