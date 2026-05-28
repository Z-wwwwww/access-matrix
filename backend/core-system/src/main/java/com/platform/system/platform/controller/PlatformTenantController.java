package com.platform.system.platform.controller;

import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.platform.dto.TenantDto;
import com.platform.system.platform.service.TenantAdminService;
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
 * Platform-ops tenant management REST surface.
 *
 * <p>Every endpoint is gated by {@code platform:tenant:*} permissions
 * which only the PLATFORM_ADMIN role carries (seeded by V26 against the
 * 'system' tenant). Business-tenant SUPER_ADMIN ({@code *:*}) does NOT
 * satisfy these checks — the {@link com.platform.core.common.security.PermissionMatcher}
 * has an explicit carve-out so the two scopes don't shadow each other.
 *
 * <p>Mounted under {@code /platform} as a hint to ops + a tripwire for
 * anyone scanning routes: anything under that prefix is platform-ops
 * surface, NOT a customer-facing API.
 */
@RestController
@RequestMapping("/platform/tenants")
public class PlatformTenantController {

    private final TenantAdminService tenantService;

    public PlatformTenantController(TenantAdminService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @RequiresPermission(PlatformPermissions.TENANT_READ)
    public JsonResult<PageResult<TenantDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return JsonResult.ok(tenantService.list(page, size, keyword));
    }

    @GetMapping("/{id}")
    @RequiresPermission(PlatformPermissions.TENANT_READ)
    public JsonResult<TenantDto.View> get(@PathVariable String id) {
        return JsonResult.ok(tenantService.get(id));
    }

    @PostMapping
    @RequiresPermission(PlatformPermissions.TENANT_CREATE)
    @OpLog(module = "platform", action = "tenant.create", targetType = "tenant")
    public JsonResult<String> create(@Valid @RequestBody TenantDto.CreateRequest body) {
        return JsonResult.ok(tenantService.create(body));
    }

    /**
     * Soft delete. Sets the registry row's {@code mark=0} and disables
     * the Keycloak realm (users can't sign in any more) but leaves the
     * tenant's business data intact. Reversible by a hard-coded
     * re-enable step (ops-only — not exposed via this API on purpose).
     */
    @DeleteMapping("/{id}")
    @RequiresPermission(PlatformPermissions.TENANT_DELETE)
    @OpLog(module = "platform", action = "tenant.softDelete", targetType = "tenant")
    public JsonResult<Void> softDelete(@PathVariable String id) {
        tenantService.softDelete(id);
        return JsonResult.ok();
    }
}
