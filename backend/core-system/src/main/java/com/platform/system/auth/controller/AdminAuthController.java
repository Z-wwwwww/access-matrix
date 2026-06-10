package com.platform.system.auth.controller;

import com.platform.system.auth.dto.UnlockRequest;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.security.SystemPermissions;
import com.platform.system.rbac.service.UserAdminService;
import com.platform.core.infrastructure.security.AccountLockoutService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account-state endpoints that sit OUTSIDE the user CRUD console: lockout
 * release and force-logout. Admin-driven password resets used to live here as
 * a typed-in local-hash write; that path is gone — they now go through
 * {@code UserAdminController /admin/user/{id}/reset-password}, which rotates a
 * temporary credential in Keycloak exactly like the platform-user console.
 */
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final UserMapper userMapper;
    private final AccountLockoutService lockoutService;
    private final UserAdminService userAdminService;

    public AdminAuthController(UserMapper userMapper, AccountLockoutService lockoutService,
                               UserAdminService userAdminService) {
        this.userMapper = userMapper;
        this.lockoutService = lockoutService;
        this.userAdminService = userAdminService;
    }

    @PostMapping("/unlock")
    @RequiresPermission(SystemPermissions.AUTH_UNLOCK)
    @OpLog(module = "system", action = "auth.unlock", targetType = "user")
    public JsonResult<Void> unlock(@Valid @RequestBody UnlockRequest body) {
        UserEntity user = userMapper.findByIdentifier(tenant(), body.username());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        lockoutService.reset(tenant(), body.username());
        user.setStatus(1);
        userMapper.updateById(user);
        return JsonResult.ok();
    }

    private static String tenant() {
        String tid = RequestContext.tenantId();
        return (tid == null || tid.isBlank()) ? "default" : tid;
    }

    /**
     * Force-logout a user — every in-flight access token issued <em>before</em>
     * this call will be rejected by the permission aspect at next API hit.
     * Requires the {@code *:*} super-permission so a kicked-out admin can't
     * grant the kick-back via a low-tier permission. Delegates to the service,
     * which refuses to kick a protected admin (built-in / tenant SUPER_ADMIN).
     */
    @PostMapping("/force-logout/{userId}")
    @RequiresPermission("*:*")
    @OpLog(module = "system", action = "auth.forceLogout", targetType = "user")
    public JsonResult<Void> forceLogout(@PathVariable String userId) {
        userAdminService.forceLogout(userId);
        return JsonResult.ok();
    }
}
