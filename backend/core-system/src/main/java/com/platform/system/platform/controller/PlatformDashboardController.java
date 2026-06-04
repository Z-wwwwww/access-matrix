package com.platform.system.platform.controller;

import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.platform.dto.PlatformDashboardDto;
import com.platform.system.platform.service.PlatformDashboardService;
import com.platform.system.security.PlatformPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only platform-ops monitoring dashboard (activation funnel / engagement /
 * reliability / security). Lives next to the tenant console and is gated by the
 * same {@code platform:tenant:read} permission — the whole platform surface is
 * already behind it, and only PLATFORM_ADMIN reaches here. No mutating endpoints.
 */
@RestController
@RequestMapping("/platform/dashboard")
public class PlatformDashboardController {

    private final PlatformDashboardService dashboardService;

    public PlatformDashboardController(PlatformDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @RequiresPermission(PlatformPermissions.TENANT_READ)
    public JsonResult<PlatformDashboardDto.View> load() {
        return JsonResult.ok(dashboardService.load());
    }
}
