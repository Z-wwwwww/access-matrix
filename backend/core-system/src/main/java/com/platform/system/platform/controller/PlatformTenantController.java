package com.platform.system.platform.controller;

import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.platform.dto.TenantDto;
import com.platform.system.platform.service.TenantAdminService;
import com.platform.system.platform.service.TenantImpersonationService;
import com.platform.system.security.PlatformPermissions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final TenantImpersonationService impersonationService;

    public PlatformTenantController(TenantAdminService tenantService,
                                    TenantImpersonationService impersonationService) {
        this.tenantService = tenantService;
        this.impersonationService = impersonationService;
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
     * Patch the registry row's mutable fields (displayName, contactEmail).
     * Also propagates the displayName change to the Keycloak realm so the
     * KC admin console and our platform console stay in sync.
     * tenant_code is immutable — see {@link TenantDto.UpdateRequest}.
     */
    @PatchMapping("/{id}")
    @RequiresPermission(PlatformPermissions.TENANT_UPDATE)
    @OpLog(module = "platform", action = "tenant.update", targetType = "tenant")
    public JsonResult<Void> update(@PathVariable String id,
                                   @Valid @RequestBody TenantDto.UpdateRequest body) {
        tenantService.update(id, body);
        return JsonResult.ok();
    }

    /**
     * Suspend the tenant: status=0, Keycloak realm disabled. Reversible
     * via {@link #resume}. The registry row stays mark=1 so the tenant
     * remains visible in the list with a "suspended" badge.
     */
    @PostMapping("/{id}/suspend")
    @RequiresPermission(PlatformPermissions.TENANT_UPDATE)
    @OpLog(module = "platform", action = "tenant.suspend", targetType = "tenant")
    public JsonResult<Void> suspend(@PathVariable String id) {
        tenantService.suspend(id);
        return JsonResult.ok();
    }

    /** Resume a previously suspended tenant: status=1, KC realm re-enabled. */
    @PostMapping("/{id}/resume")
    @RequiresPermission(PlatformPermissions.TENANT_UPDATE)
    @OpLog(module = "platform", action = "tenant.resume", targetType = "tenant")
    public JsonResult<Void> resume(@PathVariable String id) {
        tenantService.resume(id);
        return JsonResult.ok();
    }

    /**
     * Mint a 30-minute "support session" token that lets the platform-ops
     * caller act with the target tenant's SUPER_ADMIN authority. Reason is
     * mandatory and lands in {@code core_oplog.request_body} via the @OpLog
     * aspect — primary audit justification. The minted token carries an
     * RFC 8693 {@code act} claim with the original ops identity, plus a
     * {@code "[support] <ops>"} username prefix so every downstream oplog
     * row shouts "this was a support session" without having to decode
     * the JWT.
     *
     * <p>FULL-mode for v1 — the token has the tenant's tenant:* wildcard.
     * READ_ONLY is tracked as a follow-up; today the audit trail is the
     * sole protection against an ops user making bad writes during support.
     *
     * <p>Built-in tenants (system, demo) refused: no operational reason to
     * impersonate them, and accidents would be unusually costly there.
     */
    @PostMapping("/{id}/support-session")
    @RequiresPermission(PlatformPermissions.TENANT_IMPERSONATE)
    @OpLog(module = "platform", action = "tenant.impersonate.start", targetType = "tenant")
    public JsonResult<TenantDto.SupportSessionResponse> startSupportSession(
            @PathVariable String id,
            @Valid @RequestBody TenantDto.SupportSessionRequest body) {
        return JsonResult.ok(impersonationService.startSession(id, body.reason()));
    }

    /**
     * Permanent hard delete — the "empty recycle bin" operation.
     *
     * <p>UX contract: tenant must already be in suspended state
     * (status=0). Operator first {@link #suspend}s, then deletes from
     * the suspended view. Single-click "active → gone" is intentionally
     * impossible.
     *
     * <p>Body carries {@code confirmCode} which must match the tenant's
     * {@code tenantCode} exactly — defence-in-depth typed confirmation.
     * The frontend gates on the same string match.
     *
     * <p>Drops business data + KC realm + registry row. Irreversible.
     */
    @DeleteMapping("/{id}")
    @RequiresPermission(PlatformPermissions.TENANT_DELETE)
    @OpLog(module = "platform", action = "tenant.hardDelete", targetType = "tenant")
    public JsonResult<Void> hardDelete(@PathVariable String id,
                                       @Valid @RequestBody TenantDto.HardDeleteRequest body) {
        tenantService.hardDelete(id, body.confirmCode());
        return JsonResult.ok();
    }
}
