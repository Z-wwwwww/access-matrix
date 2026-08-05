package com.platform.system.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Startup check that {@link TenantAdminService#TENANT_PURGE_TABLES} still covers
 * every per-tenant table in the schema.
 *
 * <h3>Why this exists</h3>
 * <p>{@code hardDelete} purges a tenant by running one {@code DELETE ... WHERE
 * tenant_id = ?} per table off a hand-maintained list. Nothing tied that list to
 * the schema, so a table added later carried on holding the deleted tenant's rows
 * — which is how {@code core_domain_event}, {@code core_notification},
 * {@code core_job} and {@code core_job_log} came to be missed. The orphans are not
 * inert: the outbox dispatcher and the platform dashboard both read
 * {@code core_domain_event} with NO tenant predicate, and
 * {@code DynamicJobScheduler} reconciles enabled {@code core_job} rows across all
 * tenants every 15 seconds — so a dead tenant's job would keep firing forever.
 *
 * <p>This is the same shape as {@code TenantSchemaGuard} (which pins the tenant
 * interceptor's exclusion list against the schema) and
 * {@code PermissionConsistencyGuard}: turn "someone has to remember" into
 * something the application notices by itself.
 *
 * <h3>Warn, not fail</h3>
 * <p>Unlike {@code TenantSchemaGuard}, an uncovered table does not break any
 * request — it only leaves residue behind a rare, operator-initiated purge. A
 * hard boot failure would be disproportionate (and would block deploys on a
 * newly-added table before its purge line is written), so this logs a loud WARN
 * naming the exact tables and the constant to add them to.
 */
@Component
public class TenantPurgeCoverageGuard {

    private static final Logger log = LoggerFactory.getLogger(TenantPurgeCoverageGuard.class);

    private static final String SCHEMA = "public";
    private static final String TENANT_COLUMN = "tenant_id";

    /**
     * Tables that carry a {@code tenant_id} column but must NOT be purged per
     * tenant. Kept explicit so "not in the purge list" always means either a
     * considered decision here or a genuine omission — never ambiguity.
     *
     * <ul>
     *   <li>{@code core_tenant} — the registry row itself, deleted by id at the
     *       very end of {@code hardDelete} (after the Keycloak realm), not by
     *       tenant_id (which is always {@code 'system'} on those rows).</li>
     *   <li>{@code core_support_session} — platform-ops audit, tenant_id
     *       {@code 'system'}; the target tenant is in {@code tenant_code}.</li>
     * </ul>
     */
    static final Set<String> NOT_PURGED_BY_DESIGN = Set.of(
            "core_tenant",
            "core_support_session");

    private final JdbcTemplate jdbc;

    public TenantPurgeCoverageGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 900)   // just after TenantSchemaGuard
    public void verify() {
        Set<String> uncovered = uncovered(loadTablesWithTenantColumn());
        if (uncovered.isEmpty()) {
            log.info("[TenantPurgeCoverageGuard] OK — every per-tenant table is covered by "
                    + "TenantAdminService.TENANT_PURGE_TABLES");
            return;
        }
        log.warn("[TenantPurgeCoverageGuard] per-tenant tables NOT purged by tenant hard-delete: {}\n"
                        + "  Their rows will survive after the tenant is gone. Add each to "
                        + "TenantAdminService.TENANT_PURGE_TABLES (FK-safe order: junctions before parents),\n"
                        + "  or — if the table is deliberately not tenant-owned data — to "
                        + "TenantPurgeCoverageGuard.NOT_PURGED_BY_DESIGN with the reason.",
                uncovered);
    }

    /**
     * Package-private and pure so the rule is unit-testable without a database:
     * the set of tenant-scoped tables that neither the purge list nor the
     * by-design exclusions account for.
     */
    static Set<String> uncovered(Set<String> tablesWithTenantColumn) {
        Set<String> covered = new LinkedHashSet<>(TenantAdminService.TENANT_PURGE_TABLES);
        covered.addAll(NOT_PURGED_BY_DESIGN);
        Set<String> out = new TreeSet<>();
        for (String t : tablesWithTenantColumn) {
            if (!covered.contains(t.toLowerCase())) out.add(t.toLowerCase());
        }
        return out;
    }

    private Set<String> loadTablesWithTenantColumn() {
        List<String> rows = jdbc.queryForList(
                "SELECT c.table_name FROM information_schema.columns c "
                        + "  JOIN information_schema.tables t "
                        + "    ON t.table_schema = c.table_schema AND t.table_name = c.table_name "
                        + " WHERE c.table_schema = ? AND c.column_name = ? "
                        + "   AND t.table_type = 'BASE TABLE'",
                String.class, SCHEMA, TENANT_COLUMN);
        Set<String> out = new LinkedHashSet<>();
        for (String r : rows) out.add(r.toLowerCase());
        return out;
    }
}
