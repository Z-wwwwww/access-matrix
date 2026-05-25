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

    /** SUPER_ADMIN role — seeded by V5 / AuthSchemaBootstrap. */
    public static final String SUPER_ADMIN_ID = "00000000000000000000ROLE01";
}
