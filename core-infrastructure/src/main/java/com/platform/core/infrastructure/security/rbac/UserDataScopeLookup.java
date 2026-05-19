package com.platform.core.infrastructure.security.rbac;

/**
 * Computes a user's full data-scope decision from persistent storage.
 *
 * <p>Defined in infrastructure so the request-scoped {@code DataScopeResolver}
 * does not have to import from {@code core-system}. The implementation lives
 * in {@code core-system} and is wired by Spring at runtime — mirrors the
 * {@link UserPermissionsLookup} pattern from Stage 1.
 */
public interface UserDataScopeLookup {

    /**
     * @param userId the subject identifier
     * @return resolved decision; never null. Returns
     *         {@link DataScopeDecision#empty(String)} if the user has no
     *         roles or all roles are disabled.
     */
    DataScopeDecision loadDecision(String userId);
}
