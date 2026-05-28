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
 *       <em>not</em> anything in the {@code platform:} namespace. Held by
 *       SUPER_ADMIN of each business tenant. A compromised business admin
 *       should not be able to reach {@code POST /platform/tenants} and
 *       create realms.</li>
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

    private PermissionMatcher() {}

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
        // Tenant super matches everything except the platform: namespace.
        if (userPerms.contains(TENANT_SUPER) && !required.startsWith(PLATFORM_NS)) return true;
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
