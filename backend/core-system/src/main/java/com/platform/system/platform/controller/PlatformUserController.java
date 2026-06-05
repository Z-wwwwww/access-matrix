package com.platform.system.platform.controller;

import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.platform.dto.PlatformUserDto;
import com.platform.system.platform.service.PlatformUserAdminService;
import com.platform.system.security.PlatformPermissions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-ops staff management: the users in the {@code system} tenant who hold
 * PLATFORM_ADMIN. Gated by {@code platform:user:*} (PLATFORM_ADMIN only — business
 * {@code tenant:*} does not match {@code platform:*}). Lets an operator add
 * further ops accounts without hand-run SQL + Keycloak.
 */
@RestController
@RequestMapping("/platform/users")
public class PlatformUserController {

    private final PlatformUserAdminService userService;

    public PlatformUserController(PlatformUserAdminService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequiresPermission(PlatformPermissions.OPSUSER_READ)
    public JsonResult<PageResult<PlatformUserDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return JsonResult.ok(userService.list(page, size, keyword));
    }

    /**
     * Provision a new platform-ops user: a Keycloak user in the {@code system}
     * realm + the {@code core_auth_user} row + a PLATFORM_ADMIN binding. Returns a
     * one-time temporary password (KC forces a change on first login).
     */
    @PostMapping
    @RequiresPermission(PlatformPermissions.OPSUSER_CREATE)
    @OpLog(module = "platform", action = "opsuser.create", targetType = "user")
    public JsonResult<PlatformUserDto.CreateResponse> create(@Valid @RequestBody PlatformUserDto.CreateRequest body) {
        return JsonResult.ok(userService.create(body));
    }

    /** Disable a platform operator (DB status + Keycloak). */
    @PostMapping("/{id}/disable")
    @RequiresPermission(PlatformPermissions.OPSUSER_UPDATE)
    @OpLog(module = "platform", action = "opsuser.disable", targetType = "user")
    public JsonResult<Void> disable(@PathVariable String id) {
        userService.setEnabled(id, false);
        return JsonResult.ok();
    }

    /** Re-enable a platform operator. */
    @PostMapping("/{id}/enable")
    @RequiresPermission(PlatformPermissions.OPSUSER_UPDATE)
    @OpLog(module = "platform", action = "opsuser.enable", targetType = "user")
    public JsonResult<Void> enable(@PathVariable String id) {
        userService.setEnabled(id, true);
        return JsonResult.ok();
    }

    /** Reset a platform operator's password — returns a one-time temporary password. */
    @PostMapping("/{id}/reset-password")
    @RequiresPermission(PlatformPermissions.OPSUSER_UPDATE)
    @OpLog(module = "platform", action = "opsuser.resetPassword", targetType = "user")
    public JsonResult<PlatformUserDto.ResetPwResponse> resetPassword(@PathVariable String id) {
        return JsonResult.ok(userService.resetPassword(id));
    }

    /** Soft-delete a platform operator + remove the Keycloak user. */
    @DeleteMapping("/{id}")
    @RequiresPermission(PlatformPermissions.OPSUSER_DELETE)
    @OpLog(module = "platform", action = "opsuser.delete", targetType = "user")
    public JsonResult<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return JsonResult.ok();
    }
}
