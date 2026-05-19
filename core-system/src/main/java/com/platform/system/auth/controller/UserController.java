package com.platform.system.auth.controller;

import com.platform.system.auth.dto.UserInfoResponse;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.service.PermissionQueryService;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    private final JwtDecoder jwtDecoder;
    private final RoleMapper roleMapper;
    private final PermissionQueryService permissionQueryService;

    public UserController(UserMapper userMapper, JwtDecoder jwtDecoder,
                          RoleMapper roleMapper, PermissionQueryService permissionQueryService) {
        this.userMapper = userMapper;
        this.jwtDecoder = jwtDecoder;
        this.roleMapper = roleMapper;
        this.permissionQueryService = permissionQueryService;
    }

    @GetMapping("/me")
    public JsonResult<UserInfoResponse> me(HttpServletRequest req) {
        String userId = extractUserId(req);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        UserEntity u = userMapper.selectById(userId);
        if (u == null || u.getMark() == null || u.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        // Roles and authorities now come from RBAC tables, not the deprecated JSONB columns.
        List<RoleEntity> roles = roleMapper.findRolesByUserId(userId);
        List<String> roleCodes = new ArrayList<>(roles.size());
        for (RoleEntity r : roles) roleCodes.add(r.getCode());
        Set<String> perms = permissionQueryService.loadUserPermissions(userId);

        UserInfoResponse resp = new UserInfoResponse(
                u.getId(),
                u.getUsername(),
                u.getUserNo(),
                u.getEmail(),
                u.getDisplayName(),
                u.getTenantId(),
                u.getDeptId(),
                roleCodes,
                new ArrayList<>(perms)
        );
        return JsonResult.ok(resp);
    }

    private String extractUserId(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return jat.getToken().getSubject();
        }
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Jwt jwt = jwtDecoder.decode(header.substring(7));
                return jwt.getSubject();
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid token");
            }
        }
        return null;
    }
}
