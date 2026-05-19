package com.platform.core.infrastructure.security.rbac;

import java.util.Set;

/**
 * Resolves a user's full permission-code set from persistent storage.
 *
 * <p>Defined here so {@link PermissionResolver} (infrastructure-layer) does not
 * have to import from {@code core-system}. The implementation lives in
 * {@code core-system} and is wired by Spring at runtime.
 */
public interface UserPermissionsLookup {

    /**
     * @param userId the subject identifier
     * @return distinct permission codes; empty set if user has none / does not exist
     */
    Set<String> loadUserPermissions(String userId);
}
