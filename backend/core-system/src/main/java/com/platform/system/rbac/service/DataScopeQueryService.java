package com.platform.system.rbac.service;

import com.platform.core.common.security.PermissionMatcher;
import com.platform.core.infrastructure.security.rbac.DataScopeDecision;
import com.platform.core.infrastructure.security.rbac.UserDataScopeLookup;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.DeptEntity;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.mapper.DeptMapper;
import com.platform.system.rbac.mapper.RoleDeptMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes a user's data-scope decision by walking their roles and merging
 * the five scope modes (ALL / DEPT_AND_SUB / DEPT / SELF / CUSTOM).
 *
 * <p>Multi-role merge rule: <b>UNION (more permissive)</b>. Once any role
 * grants ALL we short-circuit; otherwise we accumulate visible dept IDs and
 * a single SELF flag.
 *
 * <p>Caffeine-cached per userId in the {@code userDataScope} cache (5 min,
 * see {@code application.yml}). Cache is invalidated by
 * {@code PermissionCacheService.evictUser/evictRole}.
 */
@Service
public class DataScopeQueryService implements UserDataScopeLookup {

    public static final int SCOPE_ALL          = 1;
    public static final int SCOPE_DEPT_AND_SUB = 2;
    public static final int SCOPE_DEPT         = 3;
    public static final int SCOPE_SELF         = 4;
    public static final int SCOPE_CUSTOM       = 5;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final DeptMapper deptMapper;
    private final PermissionQueryService permissionQueryService;

    public DataScopeQueryService(UserMapper userMapper,
                                 RoleMapper roleMapper,
                                 RoleDeptMapper roleDeptMapper,
                                 DeptMapper deptMapper,
                                 PermissionQueryService permissionQueryService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.deptMapper = deptMapper;
        this.permissionQueryService = permissionQueryService;
    }

    @Override
    @Cacheable(value = "userDataScope", key = "#userId", unless = "#result == null")
    public DataScopeDecision loadDecision(String userId) {
        if (userId == null || userId.isBlank()) return DataScopeDecision.empty(null);

        // Super admins (anyone holding *:*) get unrestricted scope.
        Set<String> perms = permissionQueryService.loadUserPermissions(userId);
        if (perms.contains(PermissionMatcher.SUPER)) {
            return DataScopeDecision.unrestricted(userId);
        }

        List<RoleEntity> roles = roleMapper.findRolesByUserId(userId);
        if (roles.isEmpty()) return DataScopeDecision.empty(userId);

        UserEntity user = userMapper.selectById(userId);
        String userDeptId = user == null ? null : user.getDeptId();
        String userDeptPath = null;
        if (userDeptId != null) {
            DeptEntity userDept = deptMapper.selectById(userDeptId);
            if (userDept != null) userDeptPath = userDept.getPath();
        }

        Set<String> visibleDeptIds = new HashSet<>();
        boolean selfOnly = false;

        for (RoleEntity role : roles) {
            Integer mode = role.getDataScope();
            if (mode == null) continue;
            switch (mode) {
                case SCOPE_ALL -> {
                    // Short-circuit: any ALL role wins outright.
                    return DataScopeDecision.unrestricted(userId);
                }
                case SCOPE_DEPT_AND_SUB -> {
                    if (userDeptPath != null) {
                        visibleDeptIds.addAll(deptMapper.findSubtreeIds(userDeptPath));
                    } else if (userDeptId != null) {
                        visibleDeptIds.add(userDeptId);
                    }
                }
                case SCOPE_DEPT -> {
                    if (userDeptId != null) visibleDeptIds.add(userDeptId);
                }
                case SCOPE_SELF -> selfOnly = true;
                case SCOPE_CUSTOM -> {
                    List<String> explicit = roleDeptMapper.findDeptIdsByRoleId(role.getId());
                    for (String d : explicit) {
                        // Each explicit dept also includes its subtree.
                        DeptEntity dept = deptMapper.selectById(d);
                        if (dept != null && dept.getPath() != null) {
                            visibleDeptIds.addAll(deptMapper.findSubtreeIds(dept.getPath()));
                        } else {
                            visibleDeptIds.add(d);
                        }
                    }
                }
                default -> { /* unknown scope code — treat as no contribution */ }
            }
        }

        return new DataScopeDecision(false, visibleDeptIds, selfOnly, userId);
    }
}
