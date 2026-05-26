package com.platform.core.bootstrap.it;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end schema invariants that we can't prove with unit tests — they
 * depend on real Postgres semantics (partial unique indexes, JSONB, default
 * values, FK constraints).
 *
 * <p>Boots a Postgres container, runs every {@code V*.sql} migration in
 * {@code classpath:db/migration}, then probes behaviour with raw JDBC. Skipped
 * automatically when Docker isn't available so contributors without Docker
 * (or restricted CI shards) can still run {@code mvn test}.
 *
 * <p>Pinned invariants:
 * <ol>
 *   <li>V20: {@code core_auth_user} {@code (tenant_id, username)} unique
 *       index — two tenants can both have a user named "admin", but a
 *       tenant cannot have two active "admin" rows.</li>
 *   <li>Partial-unique-on-mark — soft-deleted ({@code mark=0}) rows don't
 *       collide with new active ({@code mark=1}) rows of the same username.
 *       Without this every soft-delete would burn the username forever.</li>
 *   <li>{@code tenant_id} column default is {@code 'default'} (the value
 *       {@code RequestContext.tenantIdOrDefault()} reads after V20).</li>
 *   <li>Schema parses cleanly under Postgres 17 (the prod target) — any
 *       syntax regression in a future {@code V*} migration fails this test.</li>
 * </ol>
 */
@Testcontainers
@EnabledIf("dockerAvailable")
@DisplayName("Multi-tenant schema invariants (Postgres + Flyway)")
class MultiTenantSchemaIT {

    @Container
    @SuppressWarnings("resource") // Testcontainers manages the container lifecycle.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("core_it")
            .withUsername("postgres")
            .withPassword("postgres");

    /** Junit 5 condition gate — referenced by {@link EnabledIf} on the class. */
    @SuppressWarnings("unused")
    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeAll
    static void runMigrations() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String ulid(String prefix) {
        String s = prefix + System.nanoTime();
        if (s.length() < 26) s = s + "X".repeat(26 - s.length());
        return s.substring(0, 26);
    }

    @Test
    @DisplayName("V20: same username allowed across two tenants")
    void sameUsernameAcrossTenants() throws SQLException {
        try (Connection c = conn()) {
            insertUser(c, ulid("A"), "tenantA", "admin");
            // Must succeed — V20's (tenant_id, username) partial unique index lets
            // each tenant have its own 'admin'.
            insertUser(c, ulid("B"), "tenantB", "admin");

            assertThat(countActiveUsersWithUsername(c, "admin")).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("V20: duplicate username inside the SAME tenant rejected")
    void duplicateUsernameInsideSameTenant() throws SQLException {
        try (Connection c = conn()) {
            insertUser(c, ulid("C"), "tenantDup", "alice");
            assertThatThrownBy(() -> insertUser(c, ulid("D"), "tenantDup", "alice"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_core_auth_user_tenant_username");
        }
    }

    @Test
    @DisplayName("Partial-unique-on-mark: soft-deleted row doesn't block a new active row")
    void softDeleteDoesNotBurnUsername() throws SQLException {
        // Real bug we want to prevent: if the unique index were full (not partial
        // on mark=1) every deleted user would permanently reserve their username.
        try (Connection c = conn()) {
            insertUser(c, ulid("E"), "tenantSoft", "bob");
            // Soft delete via UpdateWrapper-equivalent SQL.
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE core_auth_user SET mark = 0 WHERE tenant_id = ? AND username = ?")) {
                ps.setString(1, "tenantSoft");
                ps.setString(2, "bob");
                int updated = ps.executeUpdate();
                assertThat(updated).isEqualTo(1);
            }

            // Reusing the username must now succeed.
            insertUser(c, ulid("F"), "tenantSoft", "bob");

            assertThat(countActiveUsersWithUsername(c, "bob")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("tenant_id column has 'default' as its DDL default")
    void tenantIdDefault() throws SQLException {
        // Insert without setting tenant_id explicitly — V2 declares the default.
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO core_auth_user (id, username, password_hash) VALUES (?, ?, ?)")) {
            ps.setString(1, ulid("G"));
            ps.setString(2, "no-tenant-set");
            ps.setString(3, "x");
            ps.executeUpdate();
        }

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT tenant_id FROM core_auth_user WHERE username = ?")) {
            ps.setString(1, "no-tenant-set");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("default");
            }
        }
    }

    @Test
    @DisplayName("V19: legacy JSONB columns (roles / authorities) are gone")
    void legacyJsonbColumnsDropped() throws SQLException {
        // V19 dropped them; if anyone reintroduces SELECT * usages that reference
        // these columns, this test reminds them the columns don't exist anymore.
        try (Connection c = conn(); Statement st = c.createStatement()) {
            assertThat(columnExists(st, "core_auth_user", "roles")).isFalse();
            assertThat(columnExists(st, "core_auth_user", "authorities")).isFalse();
        }
    }

    @Test
    @DisplayName("V18: core_rbac_role.code column dropped")
    void roleCodeColumnDropped() throws SQLException {
        // The role.code → BuiltInRoles.SUPER_ADMIN_ID architectural shift; column gone.
        try (Connection c = conn(); Statement st = c.createStatement()) {
            assertThat(columnExists(st, "core_rbac_role", "code")).isFalse();
        }
    }

    @Test
    @DisplayName("Seeded SUPER_ADMIN role id is exactly BuiltInRoles.SUPER_ADMIN_ID")
    void seededSuperAdminIdMatchesConstant() throws SQLException {
        // BuiltInRolesTest pins the Java constant. This test pins the DB side —
        // if anyone changes the V5 seed without updating BuiltInRoles, the lookup
        // path fails silently in prod (role-not-found → no super-admin guard).
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id FROM core_rbac_role WHERE id = '00000000000000000000ROLE01'")) {
            assertThat(rs.next())
                    .as("seeded super-admin row with id 00000000000000000000ROLE01 must exist")
                    .isTrue();
        }
    }

    // ---------- helpers ----------

    private void insertUser(Connection c, String id, String tenantId, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO core_auth_user (id, tenant_id, username, password_hash) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, username);
            ps.setString(4, "$2a$10$placeholder");
            ps.executeUpdate();
        }
    }

    private long countActiveUsersWithUsername(Connection c, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM core_auth_user WHERE username = ? AND mark = 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private boolean columnExists(Statement st, String table, String column) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT 1 FROM information_schema.columns "
                        + " WHERE table_name = '" + table + "' AND column_name = '" + column + "'")) {
            return rs.next();
        }
    }
}
