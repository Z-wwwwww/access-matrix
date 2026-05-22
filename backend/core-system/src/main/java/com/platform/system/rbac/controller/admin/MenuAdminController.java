package com.platform.system.rbac.controller.admin;

import com.platform.system.rbac.dto.MenuAdminDto;
import com.platform.system.rbac.service.MenuAdminService;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.security.SystemPermissions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/menu")
public class MenuAdminController {

    private final MenuAdminService service;

    public MenuAdminController(MenuAdminService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @RequiresPermission(SystemPermissions.MENU_READ)
    public JsonResult<List<MenuAdminDto.View>> list() {
        return JsonResult.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @RequiresPermission(SystemPermissions.MENU_READ)
    public JsonResult<MenuAdminDto.View> get(@PathVariable String id) {
        return JsonResult.ok(service.get(id));
    }

    @PostMapping
    @RequiresPermission(SystemPermissions.MENU_CREATE)
    @OpLog(module = "system", action = "menu.create", targetType = "menu")
    public JsonResult<String> create(@Valid @RequestBody MenuAdminDto.CreateRequest req) {
        return JsonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission(SystemPermissions.MENU_UPDATE)
    @OpLog(module = "system", action = "menu.update", targetType = "menu")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody MenuAdminDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(SystemPermissions.MENU_DELETE)
    @OpLog(module = "system", action = "menu.delete", targetType = "menu")
    public JsonResult<Void> delete(@PathVariable String id) {
        service.delete(id);
        return JsonResult.ok();
    }
}
