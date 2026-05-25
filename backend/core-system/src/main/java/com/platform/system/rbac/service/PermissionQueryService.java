package com.platform.system.rbac.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.security.rbac.UserPermissionsLookup;
import com.platform.system.rbac.mapper.PermissionMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a user's permission codes from the RBAC tables (3-table JOIN) and
 * caches the result in Caffeine. Acts as the {@link UserPermissionsLookup}
 * the infrastructure-layer {@code PermissionResolver} depends on.
 */
@Service
public class PermissionQueryService implements UserPermissionsLookup {

    private final PermissionMapper permissionMapper;

    public PermissionQueryService(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Override
    @Cacheable(value = "userPermissions", key = "#userId", unless = "#result.isEmpty()")
    public Set<String> loadUserPermissions(String userId) {
        if (userId == null || userId.isBlank()) return Set.of();
        // Tenant is taken from RequestContext (post-auth: JWT tid claim; pre-auth: X-Tenant-Id header).
        List<String> codes = permissionMapper.findPermissionCodesByUserId(
                userId, RequestContext.tenantIdOrDefault());
        return codes.isEmpty() ? Set.of() : new HashSet<>(codes);
    }
}
