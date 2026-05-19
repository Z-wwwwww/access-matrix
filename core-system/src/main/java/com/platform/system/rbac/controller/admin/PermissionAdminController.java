package com.platform.system.rbac.controller.admin;

import com.platform.system.rbac.dto.PermissionDto;
import com.platform.system.rbac.service.PermissionAdminService;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/permission")
public class PermissionAdminController {

    private final PermissionAdminService service;

    public PermissionAdminController(PermissionAdminService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @RequiresPermission("permission:read")
    public JsonResult<PageResult<PermissionDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module) {
        return JsonResult.ok(service.list(page, size, keyword, module));
    }

    @GetMapping("/by-module")
    @RequiresPermission("permission:read")
    public JsonResult<Map<String, List<PermissionDto.View>>> byModule() {
        return JsonResult.ok(service.listByModule());
    }

    @PostMapping
    @RequiresPermission("permission:create")
    @OpLog(module = "system", action = "permission.create", targetType = "permission")
    public JsonResult<String> create(@Valid @RequestBody PermissionDto.CreateRequest req) {
        return JsonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission("permission:update")
    @OpLog(module = "system", action = "permission.update", targetType = "permission")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody PermissionDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("permission:delete")
    @OpLog(module = "system", action = "permission.delete", targetType = "permission")
    public JsonResult<Void> delete(@PathVariable String id) {
        service.delete(id);
        return JsonResult.ok();
    }
}
