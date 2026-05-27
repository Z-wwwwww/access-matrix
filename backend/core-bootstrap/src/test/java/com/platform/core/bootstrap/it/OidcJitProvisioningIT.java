package com.platform.core.bootstrap.it;

import com.platform.core.common.context.RequestContext;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end JIT-provisioning test against a real Keycloak + Postgres + Redis
 * stack. No mocks of Keycloak / PG / JWKS — verifies the full chain:
 *
 *   1. Boot a Keycloak container, import the committed default realm
 *      (infra/keycloak/realms/default-realm.json), create a test user
 *      via Admin API.
 *   2. Boot Postgres + Redis containers; Flyway runs all V*.sql migrations.
 *   3. Spring Boot starts in mode=oidc, pointing JWKS at the test Keycloak.
 *   4. Acquire a real access_token via OIDC password grant against the
 *      test Keycloak.
 *   5. Hit an authenticated endpoint with that token. Assert:
 *        - response is 200 (verification + RequestContext set)
 *        - core_auth_user has a NEW row with keycloak_id == JWT sub
 *        - second call hits the fast path: no new INSERT.
 *
 * Docker-gated via {@code @EnabledIf} so contributors without Docker still
 * have a green build.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// disabledWithoutDocker=true short-circuits class init when there is no
// Docker daemon — important because the @Container static fields below
// touch the docker socket during JVM class-loading, before @EnabledIf
// would otherwise get a chance to skip.
@Testcontainers(disabledWithoutDocker = true)
class OidcJitProvisioningIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("core_it")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    @SuppressWarnings("resource")
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
            // Filename matches what maven-resources-plugin copies into
            // target/test-classes from infra/keycloak/realms/. See
            // core-bootstrap/pom.xml <build><testResources>.
            .withRealmImportFile("/default-realm.json");

    /** Hardcoded test user we provision into Keycloak after the realm boots. */
    private static final String TEST_USERNAME = "jit-test-user";
    private static final String TEST_EMAIL    = "jit-test-user@default.local";
    private static final String TEST_PASSWORD = "JitTest!23";
    private static String       testUserKeycloakUuid;

    @BeforeAll
    static void seedKeycloakUser() {
        // testcontainers-keycloak mounts the file at /opt/keycloak/data/import
        // when withRealmImportFile is used (it looks under the test classpath).
        // We copy our committed default-realm.json into the test classpath
        // as a setup step so the container sees it.
        // (Step done at build time via maven-resources-plugin in the IT
        //  classpath — see comment block at the bottom of this file.)

        Keycloak admin = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .build();

        UserRepresentation u = new UserRepresentation();
        u.setUsername(TEST_USERNAME);
        u.setEmail(TEST_EMAIL);
        u.setFirstName("Jit");
        u.setLastName("Tester");
        u.setEnabled(true);
        u.setEmailVerified(true);
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(TEST_PASSWORD);
        cred.setTemporary(false);
        u.setCredentials(List.of(cred));

        try (Response r = admin.realm("default").users().create(u)) {
            int status = r.getStatus();
            // 201 = created; 409 = already exists from a previous run (we
            // share the imported realm across tests so this is harmless).
            if (status != 201 && status != 409) {
                throw new IllegalStateException(
                        "Failed to seed test user in Keycloak: HTTP " + status);
            }
        }

        // Read the user back to grab its UUID — needed by assertions later.
        List<UserRepresentation> matches = admin.realm("default").users().search(TEST_USERNAME, true);
        Assumptions.assumeTrue(matches != null && !matches.isEmpty(),
                "test user creation succeeded but lookup failed; aborting IT");
        testUserKeycloakUuid = matches.get(0).getId();
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry r) {
        // Datasource → PG container.
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        // Redis → container.
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        r.add("spring.data.redis.password", () -> "");
        // OIDC: point Spring Security + our KeycloakUserService at the
        // ephemeral container, not the local-dev :8180.
        r.add("app.security.mode", () -> "oidc");
        // Multi-realm trust prefix — the SaaS path. Single-realm `issuer-uri`
        // is intentionally NOT set so this IT exercises the same code path
        // production runs (MultiRealmJwtDecoder, OidcJitUserService's
        // issuer-base prefix check).
        r.add("app.security.oidc.issuer-base-uri", KEYCLOAK::getAuthServerUrl);
        r.add("app.keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
        r.add("app.keycloak.admin.realm",      () -> "master");
        r.add("app.keycloak.admin.client-id",  () -> "admin-cli");
        r.add("app.keycloak.admin.username",   KEYCLOAK::getAdminUsername);
        r.add("app.keycloak.admin.password",   KEYCLOAK::getAdminPassword);
        // Mail off — invite flows aren't under test here.
        r.add("app.mail.enabled", () -> "false");
        // Realm-export-for-it.json is the committed default-realm.json copied
        // into test resources by maven-resources-plugin; testcontainers-keycloak
        // mounts everything under the test classpath into the container's
        // /opt/keycloak/data/import. No additional config needed.
    }

    @Autowired UserMapper userMapper;
    @Value("${local.server.port}") int serverPort;

    @Test
    void firstCallProvisionsBusinessUser_secondCallTakesFastPath() throws Exception {
        // Confirm clean slate: no row for our test user yet.
        assertThat(userMapper.findByKeycloakIdAndTenant(testUserKeycloakUuid, "default")).isNull();

        String accessToken = acquireAccessToken();
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + serverPort + "/api")
                .build();

        // ── First call: triggers JIT provisioning. ──────────────────
        ResponseEntity<String> res1 = client.get()
                .uri("/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Tenant-Id", "default")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(String.class);
        assertThat(res1.getStatusCode().value()).isEqualTo(200);
        assertThat(res1.getBody()).contains(TEST_USERNAME);

        UserEntity provisioned = userMapper.findByKeycloakIdAndTenant(testUserKeycloakUuid, "default");
        assertThat(provisioned)
                .as("JIT must have inserted a core_auth_user row for the OIDC identity")
                .isNotNull();
        assertThat(provisioned.getKeycloakId()).isEqualTo(testUserKeycloakUuid);
        assertThat(provisioned.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(provisioned.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(provisioned.getTenantId()).isEqualTo("default");
        // Business ULID, NOT the Keycloak UUID — these are two different id spaces.
        assertThat(provisioned.getId()).hasSize(26);
        assertThat(provisioned.getId()).isNotEqualTo(testUserKeycloakUuid);

        // ── Second call: fast path, no new row. ─────────────────────
        long countBefore = userMapper.selectCount(null);
        ResponseEntity<String> res2 = client.get()
                .uri("/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Tenant-Id", "default")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(String.class);
        assertThat(res2.getStatusCode().value()).isEqualTo(200);
        long countAfter = userMapper.selectCount(null);
        assertThat(countAfter)
                .as("Fast path: second call must not insert another row")
                .isEqualTo(countBefore);

        RequestContext.clear();
    }

    /**
     * Acquire a real OIDC token via password grant against the test Keycloak.
     * Uses access-matrix-backend (the public client baked into the imported
     * realm) and the seeded user credentials.
     */
    private String acquireAccessToken() {
        Keycloak userKc = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("default")
                .clientId("access-matrix-backend")
                .grantType("password")
                .username(TEST_USERNAME)
                .password(TEST_PASSWORD)
                .scope("openid")
                .build();
        return userKc.tokenManager().getAccessTokenString();
    }
}

/*
  Realm import note:

  The committed infra/keycloak/realms/default-realm.json is copied into the
  test classpath at build time so the testcontainers-keycloak mount finds
  it. See core-bootstrap/pom.xml <build><testResources> for the wiring.
  Without that copy the container boots an empty realm and the
  access-matrix-backend client + tid mapper are missing.
*/
