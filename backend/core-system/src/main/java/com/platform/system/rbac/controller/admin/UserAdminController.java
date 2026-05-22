package com.platform.system.rbac.controller.admin;

import com.platform.system.rbac.dto.UserDto;
import com.platform.system.rbac.service.UserAdminService;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.security.SystemPermissions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/user")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @RequiresPermission(SystemPermissions.USER_READ)
    public JsonResult<PageResult<UserDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deptId) {
        return JsonResult.ok(service.list(page, size, keyword, deptId));
    }

    @GetMapping("/{id}")
    @RequiresPermission(SystemPermissions.USER_READ)
    public JsonResult<UserDto.View> get(@PathVariable String id) {
        return JsonResult.ok(service.get(id));
    }

    @PostMapping
    @RequiresPermission(SystemPermissions.USER_CREATE)
    @OpLog(module = "system", action = "user.create", targetType = "user")
    public JsonResult<String> create(@Valid @RequestBody UserDto.CreateRequest req) {
        return JsonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission(SystemPermissions.USER_UPDATE)
    @OpLog(module = "system", action = "user.update", targetType = "user")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody UserDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(SystemPermissions.USER_DELETE)
    @OpLog(module = "system", action = "user.delete", targetType = "user")
    public JsonResult<Void> delete(@PathVariable String id) {
        service.delete(id);
        return JsonResult.ok();
    }

    @GetMapping("/{id}/roles")
    @RequiresPermission(SystemPermissions.USER_READ)
    public JsonResult<List<String>> roles(@PathVariable String id) {
        return JsonResult.ok(service.listRoleIds(id));
    }

    @PutMapping("/{id}/roles")
    @RequiresPermission(SystemPermissions.USER_UPDATE)
    @OpLog(module = "system", action = "user.assignRoles", targetType = "user")
    public JsonResult<Void> assignRoles(@PathVariable String id,
                                        @Valid @RequestBody UserDto.AssignRolesRequest req) {
        service.assignRoles(id, req.roleIds());
        return JsonResult.ok();
    }

    @PutMapping("/{id}/dept")
    @RequiresPermission(SystemPermissions.USER_UPDATE)
    @OpLog(module = "system", action = "user.changeDept", targetType = "user")
    public JsonResult<Void> changeDept(@PathVariable String id,
                                       @Valid @RequestBody UserDto.ChangeDeptRequest req) {
        service.changeDept(id, req.deptId());
        return JsonResult.ok();
    }

    @PutMapping("/{id}/status")
    @RequiresPermission(SystemPermissions.USER_UPDATE)
    @OpLog(module = "system", action = "user.changeStatus", targetType = "user")
    public JsonResult<Void> changeStatus(@PathVariable String id,
                                         @Valid @RequestBody UserDto.ChangeStatusRequest req) {
        service.changeStatus(id, req.status());
        return JsonResult.ok();
    }
}
