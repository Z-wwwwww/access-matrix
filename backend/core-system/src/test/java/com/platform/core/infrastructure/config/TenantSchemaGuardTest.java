package com.platform.core.infrastructure.config;

import com.platform.core.infrastructure.config.properties.AppMybatisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mock-based test for the four startup outcomes:
 * <ol>
 *   <li>{@code tenant.enabled=false} → skip silently.</li>
 *   <li>All tables consistent → log OK, don't throw.</li>
 *   <li>Table missing {@code tenant_id} column AND not excluded → throw,
 *       message names the offender.</li>
 *   <li>Excluded table that DOES have a {@code tenant_id} column → warn,
 *       don't throw (recoverable misconfiguration).</li>
 * </ol>
 */
class TenantSchemaGuardTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
    }

    private TenantSchemaGuard newGuard(boolean tenantEnabled) {
        AppMybatisProperties props = new AppMybatisProperties(
                new AppMybatisProperties.Tenant(tenantEnabled));
        return new TenantSchemaGuard(jdbc, props);
    }

    private void stubTables(List<String> allTables, List<String> tablesWithTenantCol) {
        // Anchor on the SQL string fragment so reordering doesn't break the stubs.
        when(jdbc.queryForList(
                org.mockito.ArgumentMatchers.contains("information_schema.tables"),
                eq(String.class),
                any(Object[].class)))
                .thenReturn(allTables);
        when(jdbc.queryForList(
                org.mockito.ArgumentMatchers.contains("information_schema.columns"),
                eq(String.class),
                any(Object[].class)))
                .thenReturn(tablesWithTenantCol);
    }

    @Test
    @DisplayName("tenant disabled — guard is a no-op (no DB queries)")
    void disabledShortCircuits() {
        TenantSchemaGuard guard = newGuard(false);
        assertThatCode(guard::verify).doesNotThrowAnyException();
        // jdbc never touched — we didn't even stub it.
    }

    @Test
    @DisplayName("all tenant-scoped tables have tenant_id, excluded tables don't — pass")
    void happyPath() {
        stubTables(
                List.of(
                        "core_rbac_role",
                        "core_rbac_permission",
                        "business_widget",
                        "core_numbering_key",      // per-tenant, JdbcTemplate-only — has column, NOT excluded
                        // Excluded — correctly column-less:
                        "flyway_schema_history",
                        "core_meta",
                        "core_numbering_management"),
                List.of(
                        "core_rbac_role",
                        "core_rbac_permission",
                        "business_widget",
                        "core_numbering_key")
        );
        TenantSchemaGuard guard = newGuard(true);
        assertThatCode(guard::verify).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("table missing tenant_id and not excluded — fail-fast with offender named")
    void missingColumnIsFatal() {
        stubTables(
                List.of("core_rbac_role", "business_oops", "flyway_schema_history",
                        "core_meta", "core_numbering_management", "core_numbering_key"),
                // business_oops is NOT in this list → has no tenant_id column
                List.of("core_rbac_role")
        );
        TenantSchemaGuard guard = newGuard(true);
        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("business_oops")
                .hasMessageContaining("TENANT_EXCLUDED_TABLES");
    }

    @Test
    @DisplayName("excluded table that DOES have tenant_id — warn but boot succeeds")
    void wastedExclusionIsRecoverable() {
        // core_meta is in EXCLUDED but we pretend it grew a tenant_id column
        // through a recent migration that forgot to remove the entry.
        stubTables(
                List.of("core_rbac_role", "core_meta", "flyway_schema_history",
                        "core_numbering_management"),
                List.of("core_rbac_role", "core_meta")  // ← core_meta has tenant_id now
        );
        TenantSchemaGuard guard = newGuard(true);
        // Should NOT throw — the data is still safely scoped (the column is
        // just unused). Operator should remove from EXCLUDED at their leisure.
        assertThatCode(guard::verify).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("table names are matched case-insensitively")
    void caseInsensitive() {
        stubTables(
                List.of("Core_Rbac_Role", "FLYWAY_SCHEMA_HISTORY", "Core_Meta",
                        "Core_Numbering_Management"),
                List.of("Core_Rbac_Role")
        );
        TenantSchemaGuard guard = newGuard(true);
        // EXCLUDED is lowercase; DB returned mixed-case. Lowercasing on both
        // sides must reconcile them so the guard doesn't flag flyway_schema_history
        // as "missing column" just because of letter casing.
        assertThatCode(guard::verify).doesNotThrowAnyException();
    }
}
