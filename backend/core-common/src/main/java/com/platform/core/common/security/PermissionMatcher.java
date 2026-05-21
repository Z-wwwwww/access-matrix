package com.platform.core.common.security;

import java.util.Set;

/**
 * Pure-function matcher used by both the AOP permission aspect and any business code
 * that needs to ask "does this user have permission X?".
 *
 * <p>Supported wildcards (kept deliberately minimal):
 * <ul>
 *   <li>{@code *:*}        — matches every permission</li>
 *   <li>{@code resource:*} — matches every action on that resource</li>
 *   <li>exact match        — e.g. {@code user:delete} matches itself</li>
 * </ul>
 *
 * <p>No support for {@code *:action}, no support for multi-level resources,
 * no SpEL — keep matching logic boring and greppable.
 */
public final class PermissionMatcher {

    public static final String SUPER = "*:*";

    private PermissionMatcher() {}

    /**
     * @param userPerms permission strings the user is known to hold
     * @param required  the permission the operation needs
     * @return true iff user satisfies the requirement under the rules above
     */
    public static boolean matches(Set<String> userPerms, String required) {
        if (required == null || required.isBlank()) return false;
        if (userPerms == null || userPerms.isEmpty()) return false;
        if (userPerms.contains(SUPER)) return true;
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
