package com.platform.core.common.security;

import java.util.Set;

/**
 * Pure-function matcher used by both the AOP permission aspect and any business code
 * that needs to ask "does this user have permission X?".
 *
 * <h3>Two super-wildcards, two scopes</h3>
 * <p>The system has two independent authority scopes — platform ops
 * (cross-tenant management) and business super-admin (within one tenant).
 * Each gets its own wildcard, and the two do NOT shadow each other:
 *
 * <ul>
 *   <li>{@code *:*} — <b>PLATFORM super</b>. Matches every permission in the
 *       {@code platform:} namespace ({@code platform:tenant:*} etc.).
 *       Held by PLATFORM_ADMIN. Does <em>not</em> match business permissions
 *       like {@code user:read} — a platform admin shouldn't be able to
 *       impersonate business-tenant users (GDPR / SOC2 privacy boundary).</li>
 *   <li>{@code tenant:*} — <b>TENANT super</b>. Matches every business
 *       permission ({@code user:read}, {@code role:create}, ...) but
 *       <em>not</em> anything in a platform-ops reserved namespace
 *       ({@code platform:} or {@code opsuser:}). Held by SUPER_ADMIN of each
 *       business tenant. A compromised business admin must not reach
 *       {@code POST /platform/tenants} (create realms) NOR the platform-user
 *       console ({@code opsuser:*}). {@code opsuser:} lives OUTSIDE
 *       {@code platform:} on purpose (so {@code platform:*}/{@code *:*} don't
 *       auto-grant it) — which means {@code tenant:*}'s "everything else" rule
 *       must explicitly carve it out too, else a tenant admin would inherit it.</li>
 * </ul>
 *
 * <p>Other wildcards:
 * <ul>
 *   <li>{@code resource:*} — matches every action on that resource
 *       (e.g. {@code user:*} → {@code user:read}, {@code user:create}, ...).
 *       Still works for {@code platform:*} too as a narrower platform-ops
 *       delegation if ever needed.</li>
 *   <li>exact match — e.g. {@code user:delete} matches itself</li>
 * </ul>
 *
 * <h3>Why the symbol assignment</h3>
 * <p>{@code *:*} <em>looks like</em> the highest-privilege wildcard, so it
 * goes to the highest-privilege role (PLATFORM_ADMIN). The naming gives
 * future code reviewers an immediate signal: "this user has *:* — they
 * own the whole platform." That's the visual convention; the actual
 * authority is enforced by the symmetric carve-outs below.
 *
 * <h3>Why no shadowing</h3>
 * <p>Neither super-wildcard satisfies the other's namespace. To grant
 * a single user both authorities (rare — usually a single "super-super-
 * admin" that the SaaS owner uses), assign BOTH {@code *:*} and
 * {@code tenant:*} explicitly. The redundancy is the point: an explicit
 * dual grant is auditable in a way a single magic wildcard is not.
 */
public final class PermissionMatcher {

    /** PLATFORM super-wildcard. Matches the {@code platform:} namespace only. */
    public static final String SUPER = "*:*";

    /**
     * TENANT super-wildcard. Matches every business permission outside the
     * {@code platform:} namespace. Held by business-tenant SUPER_ADMIN.
     */
    public static final String TENANT_SUPER = "tenant:*";

    /**
     * Reserved namespace for platform-ops permissions. {@link #TENANT_SUPER}
     * does NOT cover this; {@link #SUPER} ONLY covers this (with the
     * symmetric carve-out for business permissions).
     */
    public static final String PLATFORM_NS = "platform:";

    /**
     * Platform-ops staff-management namespace ({@code opsuser:read/create/...}).
     * Deliberately OUTSIDE {@link #PLATFORM_NS} so {@code platform:*}/{@code *:*}
     * don't auto-grant it — only an explicit {@code opsuser:*} grant does. Like
     * {@link #PLATFORM_NS}, it is reserved away from the business
     * {@link #TENANT_SUPER} wildcard.
     */
    public static final String OPSUSER_NS = "opsuser:";

    private PermissionMatcher() {}

    /** Platform-ops reserved namespaces — not reachable via {@link #TENANT_SUPER}. */
    private static boolean isPlatformOpsReserved(String required) {
        return required.startsWith(PLATFORM_NS) || required.startsWith(OPSUSER_NS);
    }

    /**
     * @param userPerms permission strings the user is known to hold
     * @param required  the permission the operation needs
     * @return true iff user satisfies the requirement under the rules above
     */
    public static boolean matches(Set<String> userPerms, String required) {
        if (required == null || required.isBlank()) return false;
        if (userPerms == null || userPerms.isEmpty()) return false;
        // Platform super matches only the platform: namespace.
        if (userPerms.contains(SUPER) && required.startsWith(PLATFORM_NS)) return true;
        // Tenant super matches every business permission — but NOT the
        // platform-ops reserved namespaces (platform: and opsuser:).
        if (userPerms.contains(TENANT_SUPER) && !isPlatformOpsReserved(required)) return true;
        if (userPerms.contains(required)) return true;
        int colon = required.indexOf(':');
        if (colon > 0) {
            String resource = required.substring(0, colon);
            return userPerms.contains(resource + ":*");
        }
        return false;
    }

    /** True iff the user has any one of the required permissions. */
    public static boolean matchesAny(Set<String> userPerms, String... required) {
        if (required == null || required.length == 0) return false;
        for (String r : required) {
            if (matches(userPerms, r)) return true;
        }
        return false;
    }
}
