package com.platform.core.common.security;

import java.util.Set;

/**
 * Pure-function matcher used by both the AOP permission aspect and any business code
 * that needs to ask "does this user have permission X?".
 *
 * <p>Supported wildcards (kept deliberately minimal):
 * <ul>
 *   <li>{@code *:*}        — matches every business-tenant permission</li>
 *   <li>{@code resource:*} — matches every action on that resource (including {@code platform:*})</li>
 *   <li>exact match        — e.g. {@code user:delete} matches itself</li>
 * </ul>
 *
 * <h3>The {@code platform:*} carve-out</h3>
 * <p>{@code *:*} (business-tenant SUPER_ADMIN's wildcard) deliberately does NOT
 * match anything in the {@code platform:} namespace. The two scopes are
 * independent: a business-tenant super-admin can do everything within
 * their tenant, but only a PLATFORM_ADMIN (holding {@code platform:*})
 * can call {@code platform:tenant:*} or other platform-ops endpoints.
 * Without this carve-out a compromised business-tenant admin could
 * reach {@code POST /platform/tenants} and create realms — a real
 * privilege-escalation vector since KC admin operations don't go
 * through the MyBatis-Plus tenant interceptor.
 */
public final class PermissionMatcher {

    public static final String SUPER = "*:*";
    /**
     * Reserved namespace for platform-ops permissions. {@code *:*} does not
     * cover this; you need {@code platform:*} (or an exact code like
     * {@code platform:tenant:create}) to satisfy a required permission in
     * this namespace.
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
        // Business-tenant SUPER does NOT shadow platform-ops permissions.
        // See class comment for the rationale.
        if (userPerms.contains(SUPER) && !required.startsWith(PLATFORM_NS)) return true;
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
