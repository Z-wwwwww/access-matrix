package com.platform.system.platform.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant hard-delete purges one table per entry in
 * {@link TenantAdminService#TENANT_PURGE_TABLES}. Nothing tied that list to the
 * schema, so four per-tenant tables added after it was written kept the deleted
 * tenant's rows: {@code core_domain_event}, {@code core_notification},
 * {@code core_job} and {@code core_job_log}.
 *
 * <p>The residue is not inert. {@code OutboxDispatcher} drains
 * {@code core_domain_event} CROSS-TENANT and {@code PlatformDashboardService}
 * counts pending / failed events with no tenant predicate, so a dead tenant's
 * events poison the reliability KPIs permanently; and
 * {@code DynamicJobScheduler.reconcileNow} re-reads every enabled
 * {@code core_job} row across all tenants every 15 s, so a deleted tenant's
 * enabled job would keep firing against data that no longer exists.
 *
 * <p>The live schema (every table carrying a {@code tenant_id} column) is
 * inlined here as the expectation. {@code TenantPurgeCoverageGuard} performs the
 * same comparison against {@code information_schema} at boot, so a table added
 * later is caught even when nobody updates this list.
 */
class TenantPurgeCoverageTest {

    /**
     * Every {@code public} BASE TABLE with a {@code tenant_id} column, as read
     * from the running dev database. Update alongside a migration that adds one.
     */
    private static final Set<String> TABLES_WITH_TENANT_ID = new LinkedHashSet<>(Set.of(
            "core_auth_login_log",
            "core_auth_user",
            "core_domain_event",
            "core_job",
            "core_job_log",
            "core_notification",
            "core_numbering_key",
            "core_numbering_management",
            "core_oplog",
            "core_password_reset_token",
            "core_rbac_dept",
            "core_rbac_permission",
            "core_rbac_role",
            "core_rbac_role_dept",
            "core_rbac_role_menu",
            "core_rbac_role_permission",
            "core_rbac_user_role",
            "core_support_session",
            "core_tenant",
            "core_user_invite",
            "demo_task"));

    @Test
    void every_per_tenant_table_is_either_purged_or_excluded_by_design() {
        assertThat(TenantPurgeCoverageGuard.uncovered(TABLES_WITH_TENANT_ID))
                .as("these tables keep the deleted tenant's rows forever")
                .isEmpty();
    }

    @Test
    void the_four_tables_that_were_missed_are_now_purged() {
        assertThat(TenantAdminService.TENANT_PURGE_TABLES)
                .contains("core_domain_event", "core_notification", "core_job", "core_job_log");
    }

    @Test
    void junction_tables_are_purged_before_the_parents_they_reference() {
        // The FKs are ON DELETE RESTRICT, so getting this backwards turns
        // hard-delete into a constraint violation halfway through.
        var order = TenantAdminService.TENANT_PURGE_TABLES;
        assertThat(order.indexOf("core_rbac_role_permission")).isLessThan(order.indexOf("core_rbac_role"));
        assertThat(order.indexOf("core_rbac_role_permission")).isLessThan(order.indexOf("core_rbac_permission"));
        assertThat(order.indexOf("core_rbac_role_dept")).isLessThan(order.indexOf("core_rbac_dept"));
        assertThat(order.indexOf("core_rbac_user_role")).isLessThan(order.indexOf("core_rbac_role"));
        assertThat(order.indexOf("core_rbac_user_role")).isLessThan(order.indexOf("core_auth_user"));
    }

    @Test
    void the_registry_row_is_not_purged_by_tenant_id() {
        // core_tenant rows all carry tenant_id='system' — a DELETE WHERE
        // tenant_id = <code> would match nothing, and a DELETE WHERE
        // tenant_id='system' would wipe the WHOLE registry. It is deleted by id,
        // last, after the Keycloak realm.
        assertThat(TenantAdminService.TENANT_PURGE_TABLES).doesNotContain("core_tenant");
        assertThat(TenantPurgeCoverageGuard.NOT_PURGED_BY_DESIGN).contains("core_tenant");
    }

    @Test
    void a_newly_added_per_tenant_table_is_reported_as_uncovered() {
        // Pins the guard itself: the whole point is that the NEXT table can't
        // slip through unnoticed.
        Set<String> withNewTable = new LinkedHashSet<>(TABLES_WITH_TENANT_ID);
        withNewTable.add("pms_reservation");

        assertThat(TenantPurgeCoverageGuard.uncovered(withNewTable))
                .containsExactly("pms_reservation");
    }
}
