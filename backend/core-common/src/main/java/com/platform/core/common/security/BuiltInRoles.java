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

    /** SUPER_ADMIN role — seeded by V5 / AuthSchemaBootstrap. Tenant-scoped (one per business tenant). */
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
