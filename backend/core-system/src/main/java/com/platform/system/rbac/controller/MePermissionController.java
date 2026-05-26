package com.platform.system.rbac.controller;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.system.rbac.service.PermissionQueryService;
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

    public MePermissionController(PermissionQueryService permissionQueryService) {
        this.permissionQueryService = permissionQueryService;
    }

    @GetMapping("/me")
    public JsonResult<Set<String>> me() {
        // See MeMenuController for the same fix rationale: business ULID via
        // RequestContext, NOT the raw JWT subject (which is the Keycloak UUID
        // in OIDC mode and doesn't appear in core_auth_user.id).
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return JsonResult.ok(permissionQueryService.loadUserPermissions(userId));
    }
}
