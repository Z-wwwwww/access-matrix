package com.platform.system.rbac.controller;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.system.rbac.service.PermissionQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Returns the current user's permission-code set. The frontend uses this for
 * button / field visibility (e.g. {@code v-permission="user:delete"}).
 *
 * <p>Distinct from {@code GET /menu/me}: that endpoint returns the menu tree
 * a user can see, while this one is for fine-grained UI gating inside a page.
 *
 * <p>Reads from {@link PermissionQueryService} which is Caffeine-cached
 * (key = userId), so revoking a permission via the admin API takes effect
 * on the next /permission/me call after {@code PermissionCacheService.evictRole}.
 */
@RestController
@RequestMapping("/permission")
public class MePermissionController {

    private final PermissionQueryService permissionQueryService;
    private final JwtDecoder jwtDecoder;

    public MePermissionController(PermissionQueryService permissionQueryService, JwtDecoder jwtDecoder) {
        this.permissionQueryService = permissionQueryService;
        this.jwtDecoder = jwtDecoder;
    }

    @GetMapping("/me")
    public JsonResult<Set<String>> me(HttpServletRequest req) {
        String userId = extractUserId(req);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return JsonResult.ok(permissionQueryService.loadUserPermissions(userId));
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
