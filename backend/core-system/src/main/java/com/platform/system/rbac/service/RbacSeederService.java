package com.platform.system.rbac.service;

import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.security.PermissionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps the RBAC scaffolding a fresh tenant needs so its first admin
 * user can actually <em>do</em> things: a {@code SUPER_ADMIN} role bound
 * to the {@code tenant:*} permission.
 *
 * <h3>No menu cloning (V41)</h3>
 * <p>Navigation menus are a single GLOBAL set now ({@code core_rbac_menu} is
 * excluded from the tenant interceptor and read without a tenant filter), so a
 * new tenant does <b>not</b> get a per-tenant menu copy. Its SUPER_ADMIN holds
 * {@code tenant:*} and sees the global menu tree via
 * {@code MenuQueryService.findAllVisible()} (filtered by permission_code), so no
 * {@code role_menu} bindings need seeding either.
 *
 * <h3>Idempotence</h3>
 * <p>Every step is gated on "does the new tenant already have this row":
 * permission by code, role by name. So a retry of a half-failed tenant
 * creation re-runs cleanly and doesn't leave orphans or duplicates.
 *
 * <h3>Architectural note</h3>
 * <p>The seeded role's name matches demo's ("Super Administrator") and
 * carries {@code is_built_in=1}, but its ULID is fresh per tenant. The
 * codebase has a few legacy spots that still compare against the
 * compile-time constant {@link com.platform.core.common.security.BuiltInRoles#SUPER_ADMIN_ID}
 * (= demo's hardcoded ULID) — those won't fire for tenants seeded here.
 * The invite flow itself doesn't rely on those checks, so the gap is
 * acceptable for this iteration; see the follow-up task to make those
 * checks tenant-aware.
 */
@Service
public class RbacSeederService {

    private static final Logger log = LoggerFactory.getLogger(RbacSeederService.class);

    /**
     * The TENANT super-wildcard. Held by every SUPER_ADMIN; matches all
     * non-{@code platform:} permissions in their tenant. See
     * {@link com.platform.core.common.security.PermissionMatcher}.
     */
    private static final String SUPER_PERM_CODE = "tenant:*";

    /**
     * Name of the auto-seeded super-admin role. Matched on by code in a few
     * places — keep this in sync with the {@code SET name = 'Tenant Super'}
     * style updates in V29 / V14. {@code is_built_in=1} prevents UI rename.
     */
    private static final String SUPER_ROLE_NAME = "Super Administrator";
    private static final String SUPER_ROLE_DESCRIPTION =
            "Built-in super admin role with tenant:* permission";

    private final JdbcTemplate jdbc;

    public RbacSeederService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Make {@code newTenant} usable for its first super admin. Idempotent
     * — re-running on a tenant that's already been seeded short-circuits
     * on each existing piece and returns the existing role id.
     *
     * @return the SUPER_ADMIN role id for {@code newTenant} (newly generated
     *         on the first call, fetched from DB on subsequent retries).
     */
    @Transactional
    public String seedDefaultsForTenant(String newTenant) {
        if (newTenant == null || newTenant.isBlank()) {
            throw new IllegalArgumentException("newTenant must not be blank");
        }
        String permId = ensureSuperPermission(newTenant);
        String roleId = ensureSuperRole(newTenant);
        ensureRolePermission(newTenant, roleId, permId);
        // Granular business permission catalog so the tenant's admin can build
        // real custom roles. Without it a new tenant only had tenant:* — which
        // is hidden from custom roles — leaving the role permission-picker empty
        // and any custom-role user with no menu (→ login redirect loop).
        seedPermissionCatalog(newTenant);
        // Menus are global (V41) — no per-tenant clone, no role_menu seeding.
        return roleId;
    }

    /**
     * Copy the granular, business-side permission catalog into {@code tenant}
     * from {@link PermissionRegistry} (the in-code source of truth). Idempotent
     * per code. Platform-ops permissions ({@code platform:*} / {@code opsuser:*}
     * — module {@code "platform"}) are intentionally excluded: they belong to the
     * {@code system} tenant only and must never be assignable inside a business
     * tenant. Wildcards (e.g. the {@code tenant:*} super perm, seeded above) are
     * skipped too — they are not assignable units.
     */
    @Transactional
    public void seedPermissionCatalog(String tenant) {
        for (PermissionRegistry.Entry e : PermissionRegistry.allEntries().values()) {
            if (PermissionRegistry.isWildcard(e.code())) continue;
            if ("platform".equals(e.module())) continue;
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM core_rbac_permission "
                            + " WHERE tenant_id = ? AND code = ? AND mark = 1",
                    Integer.class, tenant, e.code());
            if (exists != null && exists > 0) continue;
            jdbc.update(
                    "INSERT INTO core_rbac_permission "
                            + "  (id, tenant_id, code, name, resource, action, module, is_built_in, mark, "
                            + "   create_user, update_user) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 1, 'rbac-seeder', 'rbac-seeder')",
                    IdGenerator.ulid(), tenant, e.code(), e.code(), e.resource(), e.action(), e.module());
        }
        log.info("[rbac-seed] ensured granular permission catalog for {}", tenant);
    }

    // ───────── permission ──────────────────────────────────────────────

    private String ensureSuperPermission(String tenant) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM core_rbac_permission "
                            + " WHERE tenant_id = ? AND code = ? AND mark = 1",
                    String.class, tenant, SUPER_PERM_CODE);
        } catch (EmptyResultDataAccessException e) {
            // fall through to insert
        }
        String id = IdGenerator.ulid();
        jdbc.update(
                "INSERT INTO core_rbac_permission "
                        + "  (id, tenant_id, code, name, resource, action, module, is_built_in, mark, "
                        + "   create_user, update_user) "
                        + "VALUES (?, ?, ?, 'Tenant Super', 'tenant', '*', 'system', 1, 1, "
                        + "        'rbac-seeder', 'rbac-seeder')",
                id, tenant, SUPER_PERM_CODE);
        log.info("[rbac-seed] inserted tenant:* permission for {} (id={})", tenant, id);
        return id;
    }

    // ───────── role ────────────────────────────────────────────────────

    private String ensureSuperRole(String tenant) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM core_rbac_role "
                            + " WHERE tenant_id = ? AND name = ? AND is_built_in = 1 AND mark = 1",
                    String.class, tenant, SUPER_ROLE_NAME);
        } catch (EmptyResultDataAccessException e) {
            // fall through
        }
        String id = IdGenerator.ulid();
        jdbc.update(
                "INSERT INTO core_rbac_role "
                        + "  (id, tenant_id, name, description, data_scope, is_built_in, status, mark, "
                        + "   create_user, update_user) "
                        + "VALUES (?, ?, ?, ?, 1, 1, 1, 1, 'rbac-seeder', 'rbac-seeder')",
                id, tenant, SUPER_ROLE_NAME, SUPER_ROLE_DESCRIPTION);
        log.info("[rbac-seed] inserted SUPER_ADMIN role for {} (id={})", tenant, id);
        return id;
    }

    private void ensureRolePermission(String tenant, String roleId, String permId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core_rbac_role_permission "
                        + " WHERE tenant_id = ? AND role_id = ? AND permission_id = ? AND mark = 1",
                Integer.class, tenant, roleId, permId);
        if (count != null && count > 0) return;
        jdbc.update(
                "INSERT INTO core_rbac_role_permission "
                        + "  (id, tenant_id, role_id, permission_id, mark, create_user, update_user) "
                        + "VALUES (?, ?, ?, ?, 1, 'rbac-seeder', 'rbac-seeder')",
                IdGenerator.ulid(), tenant, roleId, permId);
    }
}
