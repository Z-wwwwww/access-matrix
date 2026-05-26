package com.platform.system.auth.controller;

import com.platform.system.auth.dto.UserInfoResponse;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.service.PermissionQueryService;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionQueryService permissionQueryService;

    public UserController(UserMapper userMapper,
                          RoleMapper roleMapper, PermissionQueryService permissionQueryService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionQueryService = permissionQueryService;
    }

    @GetMapping("/me")
    public JsonResult<UserInfoResponse> me() {
        // RequestContext.userId is the BUSINESS ULID (CoreRequestContextFilter
        // injects it via OidcUserResolver for OIDC mode; in jwt mode the JWT
        // subject IS already the business ULID since we sign our own tokens).
        // We must NOT read JWT.subject directly here — in OIDC mode that's the
        // Keycloak UUID, which doesn't match any core_auth_user.id, so the
        // selectById below would silently return null and the user would see
        // a 404 with no roles / no menu / no perms.
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        UserEntity u = userMapper.selectById(userId);
        if (u == null || u.getMark() == null || u.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        // Roles and authorities now come from RBAC tables, not the deprecated JSONB columns.
        // We expose role IDs (not the user-facing name) so the frontend's role-based checks
        // stay stable across admin renames — BuiltInRoles.SUPER_ADMIN_ID is the canonical key.
        List<RoleEntity> roles = roleMapper.findRolesByUserId(userId, u.getTenantId());
        List<String> roleIds = new ArrayList<>(roles.size());
        for (RoleEntity r : roles) roleIds.add(r.getId());
        Set<String> perms = permissionQueryService.loadUserPermissions(userId);

        UserInfoResponse resp = new UserInfoResponse(
                u.getId(),
                u.getUsername(),
                u.getUserNo(),
                u.getEmail(),
                u.getDisplayName(),
                u.getTenantId(),
                u.getDeptId(),
                roleIds,
                new ArrayList<>(perms)
        );
        return JsonResult.ok(resp);
    }
}
