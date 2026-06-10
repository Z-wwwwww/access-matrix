package com.platform.system.rbac.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup self-heal: make sure EVERY existing tenant's permission catalog
 * carries the full set of granular business permissions declared in code.
 *
 * <h3>Why this exists</h3>
 * {@code core_rbac_permission} is per-tenant. {@link RbacSeederService} seeds a
 * tenant's catalog only at <em>creation</em> time, and
 * {@code PermissionConsistencyGuard} only ensures a code exists in the
 * {@code system} tenant (it runs in the system context). So when a developer
 * ships a NEW feature with new permission codes, tenants created <em>before</em>
 * that ship would never see them in the role permission-picker — the exact
 * "no permission to select" gap that left custom roles empty.
 *
 * <p>This reconciler closes that gap: on every boot it walks all business
 * tenants and re-runs {@link RbacSeederService#seedPermissionCatalog(String)}
 * (idempotent — inserts only the codes a tenant is missing). Net effect: add a
 * feature, restart the backend, and the new permission shows up for every
 * tenant automatically. New tenants are unaffected (already seeded on create);
 * the {@code system} tenant is skipped (it owns the platform-ops catalog, and
 * the business catalog intentionally excludes {@code platform:}/{@code opsuser:}).
 *
 * <p>Ordering vs {@code PermissionConsistencyGuard} is immaterial: this only
 * INSERTS codes that are in {@link com.platform.core.common.security.PermissionRegistry},
 * while the guard's orphan cleanup only REMOVES codes that are NOT — disjoint sets.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantPermissionCatalogReconciler {

    private static final Logger log = LoggerFactory.getLogger(TenantPermissionCatalogReconciler.class);

    private final JdbcTemplate jdbc;
    private final RbacSeederService seeder;

    public TenantPermissionCatalogReconciler(JdbcTemplate jdbc, RbacSeederService seeder) {
        this.jdbc = jdbc;
        this.seeder = seeder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        // Raw JdbcTemplate is not rewritten by the MyBatis-Plus tenant
        // interceptor, so this lists every live tenant cross-tenant.
        List<String> tenants = jdbc.queryForList(
                "SELECT tenant_code FROM core_tenant WHERE mark = 1 AND tenant_code <> 'system'",
                String.class);
        int ok = 0;
        for (String tenant : tenants) {
            if (tenant == null || tenant.isBlank()) continue;
            try {
                seeder.seedPermissionCatalog(tenant);
                ok++;
            } catch (RuntimeException e) {
                // One tenant's failure must not abort boot or block the others.
                log.warn("[perm-catalog] reconcile failed for tenant {}: {}", tenant, e.toString());
            }
        }
        log.info("[perm-catalog] reconciled granular permission catalog across {} tenant(s)", ok);
    }
}
