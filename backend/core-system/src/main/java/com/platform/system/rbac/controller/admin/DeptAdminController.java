package com.platform.system.rbac.controller.admin;

import com.platform.system.rbac.dto.DeptAdminDto;
import com.platform.system.rbac.service.DeptAdminService;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.security.SystemPermissions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/dept")
public class DeptAdminController {

    private final DeptAdminService service;

    public DeptAdminController(DeptAdminService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresPermission(SystemPermissions.DEPT_CREATE)
    @OpLog(module = "system", action = "dept.create", targetType = "dept")
    public JsonResult<String> create(@Valid @RequestBody DeptAdminDto.CreateRequest req) {
        return JsonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission(SystemPermissions.DEPT_UPDATE)
    @OpLog(module = "system", action = "dept.update", targetType = "dept")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody DeptAdminDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(SystemPermissions.DEPT_DELETE)
    @OpLog(module = "system", action = "dept.delete", targetType = "dept")
    public JsonResult<Void> delete(@PathVariable String id) {
        service.delete(id);
        return JsonResult.ok();
    }
}
