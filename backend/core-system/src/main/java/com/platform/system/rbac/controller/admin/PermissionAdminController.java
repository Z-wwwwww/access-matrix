package com.platform.system.rbac.controller.admin;

import com.platform.system.rbac.dto.PermissionDto;
import com.platform.system.rbac.service.PermissionAdminService;
import com.platform.system.security.SystemPermissions;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 权限字典只读视图。
 *
 * <p>历史上本控制器有 POST / PUT / DELETE。改为「常量是唯一真源」模型后，
 * 字典行由 {@code PermissionConsistencyGuard} 在启动期从 {@code PermissionRegistry}
 * 自动 upsert，UI 不再有任何写入入口，因此那些端点已删除。
 */
@RestController
@RequestMapping("/admin/permission")
public class PermissionAdminController {

    private final PermissionAdminService service;

    public PermissionAdminController(PermissionAdminService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @RequiresPermission(SystemPermissions.PERMISSION_READ)
    public JsonResult<PageResult<PermissionDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module) {
        return JsonResult.ok(service.list(page, size, keyword, module));
    }

    /** Role 編集の権限ピッカーで使用：module ごとにグルーピングして返す。 */
    @GetMapping("/by-module")
    @RequiresPermission(SystemPermissions.PERMISSION_READ)
    public JsonResult<Map<String, List<PermissionDto.View>>> byModule() {
        return JsonResult.ok(service.listByModule());
    }
}
