package com.platform.core.common.security;

/**
 * Seeded ULIDs for built-in roles. Internal lookups go through these
 * constants instead of a code/name string, so the user-facing {@code name}
 * column can be freely renamed by admins without breaking anything.
 *
 * <p>Mirrors the {@code MENU01..MENU07} / {@code PERM20..PERM23} pattern
 * already used for built-in menus and permissions — the seed IDs are
 * declared at migration time and immutable from then on.
 */
public final class BuiltInRoles {

    private BuiltInRoles() {}

    /**
     * SUPER_ADMIN role for the <b>demo</b> tenant. Seeded by V5 with this
     * fixed ULID. Every other business tenant gets a fresh random ULID
     * when {@code RbacSeederService.seedDefaultsForTenant} runs at tenant
     * creation — so this constant is correct <em>only for the demo tenant</em>.
     *
     * <p>Use {@code BuiltInRoleLookup.superAdminRoleId(tenantId)} for any
     * "is user X a super admin in tenant Y" question. This constant is
     * reserved for:
     * <ul>
     *   <li>{@code LocalAdminSeeder} bootstrap (demo-specific by design)</li>
     *   <li>Tests using demo as their fixture tenant</li>
     * </ul>
     *
     * <p>Direct {@code roleIds.contains(SUPER_ADMIN_ID)} checks in production
     * code are a bug — they silently evaluate false for every non-demo
     * tenant and disable whichever guard they were protecting.
     */
    public static final String SUPER_ADMIN_ID = "00000000000000000000ROLE01";

    /**
     * PLATFORM_ADMIN role — seeded by V26 under the 'system' tenant.
     *
     * <p>Cross-tenant authority. Holders are the SaaS operator's own staff
     * (NOT business-tenant administrators) and have the {@code platform:*}
     * wildcard permission — they can manage tenants, see cross-tenant
     * analytics, and run break-glass administration. They explicitly do
     * NOT have {@code *:*} so they cannot impersonate business-tenant users.
     *
     * <p>The 'system' tenant where this role lives is treated specially by
     * the MyBatis-Plus tenant interceptor: queries from a system-tenant
     * caller bypass the {@code WHERE tenant_id = ?} injection, which is
     * what enables cross-tenant operations.
     */
    public static final String PLATFORM_ADMIN_ID = "00000000000000000000ROLE50";
}
