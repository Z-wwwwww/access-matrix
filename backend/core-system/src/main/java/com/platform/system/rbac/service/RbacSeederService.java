package com.platform.system.rbac.service;

import com.platform.core.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bootstraps the RBAC scaffolding a fresh tenant needs so its first admin
 * user can actually <em>do</em> things: a {@code SUPER_ADMIN} role bound
 * to the {@code tenant:*} permission, plus a clone of demo's menu tree
 * with the corresponding role-menu bindings.
 *
 * <h3>Why clone instead of share globally</h3>
 * <p>The schema is per-tenant: every RBAC table has a {@code tenant_id}
 * column and the MyBatis tenant interceptor scopes every query to the
 * caller's tenant. Sharing menus globally would require dropping
 * {@code tenant_id} from {@code core_rbac_menu} and refactoring every
 * read path. Cloning keeps the existing isolation model intact and stays
 * symmetric with {@link com.platform.core.infrastructure.numbering.NumberingService#seedDefaultsForTenant}.
 *
 * <h3>Why JdbcTemplate</h3>
 * <p>This is a one-shot bulk copy with FK-aware ordering (perm → role →
 * binding; menu → role-menu). MyBatis-Plus's tenant interceptor would
 * interfere with cross-tenant SELECTs (we read from {@code demo} while
 * writing to the new tenant) — JdbcTemplate sidesteps it entirely. The
 * platform-ops caller has {@code tid='system'} which the interceptor
 * already bypasses anyway, but going through Jdbc makes the intent
 * explicit and the tests easier to mock.
 *
 * <h3>Idempotence</h3>
 * <p>Every step is gated on "does the new tenant already have this row":
 * permission by code, role by name, menus by counting active rows. So
 * a retry of a half-failed tenant creation re-runs cleanly and doesn't
 * leave orphans or duplicates.
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

    /** Source tenant for the menu clone. Same convention as NumberingService. */
    private static final String TEMPLATE_TENANT = "demo";

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
        cloneMenusAndBindings(newTenant, roleId);
        return roleId;
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

    // ───────── menus + bindings ────────────────────────────────────────

    /**
     * Clone every active menu row from {@code demo} into {@code newTenant}
     * with fresh ULIDs, then create role-menu bindings on the new SUPER_ADMIN
     * role for each menu demo's SUPER_ADMIN can see.
     *
     * <p>Parent-child relationships are preserved by building a complete
     * {@code old_id → new_id} map BEFORE inserting, so the {@code parent_id}
     * column in the new rows can be remapped in the same INSERT pass.
     *
     * <p>Skips entirely if the new tenant already has any active menu rows
     * — protects against re-running on a tenant whose operator has
     * customized the menu tree.
     */
    private void cloneMenusAndBindings(String newTenant, String newRoleId) {
        Integer existingMenus = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core_rbac_menu WHERE tenant_id = ? AND mark = 1",
                Integer.class, newTenant);
        if (existingMenus != null && existingMenus > 0) {
            log.info("[rbac-seed] {} already has {} active menus — skipping menu clone",
                    newTenant, existingMenus);
            return;
        }

        List<Map<String, Object>> demoMenus = jdbc.queryForList(
                "SELECT id, parent_id, code, title, menu_type, path, component, icon, "
                        + "       sort_order, hide, hide_footer, hide_sidebar, tab_unique, "
                        + "       redirect, permission_code, title_i18n, status "
                        + "  FROM core_rbac_menu "
                        + " WHERE tenant_id = ? AND mark = 1 "
                        + " ORDER BY (parent_id IS NULL) DESC, sort_order, id",
                TEMPLATE_TENANT);

        if (demoMenus.isEmpty()) {
            log.warn("[rbac-seed] template tenant '{}' has no menus — new tenant '{}' will have an empty sidebar",
                    TEMPLATE_TENANT, newTenant);
            return;
        }

        // Build old_id → new_id BEFORE inserting any rows, so parent_id
        // remap can be done in a single pass. CHAR(26) values come back
        // space-padded from PostgreSQL JDBC for the original id column
        // — trim() to keep map keys canonical.
        Map<String, String> idMap = new HashMap<>();
        for (Map<String, Object> row : demoMenus) {
            String oldId = ((String) row.get("id")).trim();
            idMap.put(oldId, IdGenerator.ulid());
        }

        for (Map<String, Object> row : demoMenus) {
            String oldId = ((String) row.get("id")).trim();
            String newId = idMap.get(oldId);
            Object oldParent = row.get("parent_id");
            String newParent = null;
            if (oldParent != null) {
                String trimmed = ((String) oldParent).trim();
                newParent = idMap.get(trimmed);
                // If we can't resolve the parent (data inconsistency in demo —
                // dangling parent_id), surface a warn and INSERT as a root
                // rather than crash the whole tenant creation.
                if (newParent == null) {
                    log.warn("[rbac-seed] menu {} in demo has unresolved parent {} — clone as root",
                            oldId, trimmed);
                }
            }
            jdbc.update(
                    "INSERT INTO core_rbac_menu "
                            + "  (id, tenant_id, parent_id, code, title, menu_type, path, component, icon, "
                            + "   sort_order, hide, hide_footer, hide_sidebar, tab_unique, "
                            + "   redirect, permission_code, title_i18n, status, mark, "
                            + "   create_user, update_user) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 1, "
                            + "        'rbac-seeder', 'rbac-seeder')",
                    newId, newTenant, newParent,
                    row.get("code"), row.get("title"), row.get("menu_type"),
                    row.get("path"), row.get("component"), row.get("icon"),
                    row.get("sort_order"), row.get("hide"), row.get("hide_footer"),
                    row.get("hide_sidebar"), row.get("tab_unique"),
                    row.get("redirect"), row.get("permission_code"),
                    row.get("title_i18n") == null ? null : row.get("title_i18n").toString(),
                    row.get("status"));
        }

        // Find demo's SUPER_ADMIN role (by name) so we know which menu bindings to clone.
        String demoSuperRoleId;
        try {
            demoSuperRoleId = jdbc.queryForObject(
                    "SELECT id FROM core_rbac_role "
                            + " WHERE tenant_id = ? AND name = ? AND is_built_in = 1 AND mark = 1",
                    String.class, TEMPLATE_TENANT, SUPER_ROLE_NAME);
        } catch (EmptyResultDataAccessException e) {
            log.warn("[rbac-seed] demo tenant has no SUPER_ADMIN role — skipping menu bindings; "
                    + "new tenant '{}' admin will need menus assigned manually", newTenant);
            return;
        }

        List<String> demoBindings = jdbc.queryForList(
                "SELECT menu_id FROM core_rbac_role_menu "
                        + " WHERE tenant_id = ? AND role_id = ? AND mark = 1",
                String.class, TEMPLATE_TENANT, demoSuperRoleId);

        int bound = 0;
        for (String oldMenuId : demoBindings) {
            String newMenuId = idMap.get(oldMenuId.trim());
            if (newMenuId == null) continue;   // demo binding points to a menu that wasn't in the active set
            jdbc.update(
                    "INSERT INTO core_rbac_role_menu "
                            + "  (id, tenant_id, role_id, menu_id, mark, create_user, update_user) "
                            + "VALUES (?, ?, ?, ?, 1, 'rbac-seeder', 'rbac-seeder')",
                    IdGenerator.ulid(), newTenant, newRoleId, newMenuId);
            bound++;
        }
        log.info("[rbac-seed] cloned {} menus + {} role-menu bindings into tenant '{}'",
                demoMenus.size(), bound, newTenant);
    }
}
