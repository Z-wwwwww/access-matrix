package com.platform.system.rbac.controller.admin;

import com.platform.system.rbac.dto.RoleDto;
import com.platform.system.rbac.service.RoleAdminService;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.security.SystemPermissions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/role")
public class RoleAdminController {

    private final RoleAdminService service;

    public RoleAdminController(RoleAdminService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @RequiresPermission(SystemPermissions.ROLE_READ)
    public JsonResult<PageResult<RoleDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return JsonResult.ok(service.list(page, size, keyword));
    }

    @GetMapping("/{id}")
    @RequiresPermission(SystemPermissions.ROLE_READ)
    public JsonResult<RoleDto.View> get(@PathVariable String id) {
        return JsonResult.ok(service.get(id));
    }

    @PostMapping
    @RequiresPermission(SystemPermissions.ROLE_CREATE)
    @OpLog(module = "system", action = "role.create", targetType = "role")
    public JsonResult<String> create(@Valid @RequestBody RoleDto.CreateRequest req) {
        return JsonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission(SystemPermissions.ROLE_UPDATE)
    @OpLog(module = "system", action = "role.update", targetType = "role")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody RoleDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(SystemPermissions.ROLE_DELETE)
    @OpLog(module = "system", action = "role.delete", targetType = "role")
    public JsonResult<Void> delete(@PathVariable String id) {
        service.delete(id);
        return JsonResult.ok();
    }

    @GetMapping("/{id}/permissions")
    @RequiresPermission(SystemPermissions.ROLE_READ)
    public JsonResult<List<String>> permissions(@PathVariable String id) {
        return JsonResult.ok(service.listPermissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    @RequiresPermission(SystemPermissions.ROLE_UPDATE)
    @OpLog(module = "system", action = "role.bindPermissions", targetType = "role")
    public JsonResult<Void> bindPermissions(@PathVariable String id, @Valid @RequestBody RoleDto.BindIdsRequest req) {
        service.bindPermissions(id, req.ids());
        return JsonResult.ok();
    }

    @GetMapping("/{id}/menus")
    @RequiresPermission(SystemPermissions.ROLE_READ)
    public JsonResult<List<String>> menus(@PathVariable String id) {
        return JsonResult.ok(service.listMenuIds(id));
    }

    @PutMapping("/{id}/menus")
    @RequiresPermission(SystemPermissions.ROLE_UPDATE)
    @OpLog(module = "system", action = "role.bindMenus", targetType = "role")
    public JsonResult<Void> bindMenus(@PathVariable String id, @Valid @RequestBody RoleDto.BindIdsRequest req) {
        service.bindMenus(id, req.ids());
        return JsonResult.ok();
    }

    @GetMapping("/{id}/depts")
    @RequiresPermission(SystemPermissions.ROLE_READ)
    public JsonResult<List<String>> depts(@PathVariable String id) {
        return JsonResult.ok(service.listDeptIds(id));
    }

    @PutMapping("/{id}/depts")
    @RequiresPermission(SystemPermissions.ROLE_UPDATE)
    @OpLog(module = "system", action = "role.bindDepts", targetType = "role")
    public JsonResult<Void> bindDepts(@PathVariable String id, @Valid @RequestBody RoleDto.BindIdsRequest req) {
        service.bindDepts(id, req.ids());
        return JsonResult.ok();
    }
}
