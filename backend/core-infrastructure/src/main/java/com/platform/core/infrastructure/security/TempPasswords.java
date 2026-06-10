package com.platform.core.infrastructure.security;

import java.security.SecureRandom;

/**
 * Shared generator for admin-issued single-use temporary passwords. Both the
 * platform-ops console ({@code PlatformUserAdminService}) and the tenant
 * business-user console ({@code UserAdminService}) reset passwords the same
 * way: rotate to a generated temp password in Keycloak (temporary=true, so the
 * user must pick their own on next login) and show it once to the admin.
 */
public final class TempPasswords {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TempPasswords() {}

    /** 16-char temp password with guaranteed upper/lower/digit to satisfy common KC policies. */
    public static String generate() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ", lower = "abcdefghijkmnpqrstuvwxyz", digit = "23456789";
        String all = upper + lower + digit;
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(RANDOM.nextInt(upper.length())));
        sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
        sb.append(digit.charAt(RANDOM.nextInt(digit.length())));
        for (int i = 0; i < 13; i++) sb.append(all.charAt(RANDOM.nextInt(all.length())));
        return sb.toString();
    }
}
