package com.platform.system.rbac.controller.admin;

import com.platform.system.rbac.dto.MenuAdminDto;
import com.platform.system.rbac.service.MenuAdminService;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
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
    @RequiresPermission("menu:read")
    public JsonResult<List<MenuAdminDto.View>> list() {
        return JsonResult.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @RequiresPermission("menu:read")
    public JsonResult<MenuAdminDto.View> get(@PathVariable String id) {
        return JsonResult.ok(service.get(id));
    }

    @PostMapping
    @RequiresPermission("menu:create")
    @OpLog(module = "system", action = "menu.create", targetType = "menu")
    public JsonResult<String> create(@Valid @RequestBody MenuAdminDto.CreateRequest req) {
        return JsonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission("menu:update")
    @OpLog(module = "system", action = "menu.update", targetType = "menu")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody MenuAdminDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("menu:delete")
    @OpLog(module = "system", action = "menu.delete", targetType = "menu")
    public JsonResult<Void> delete(@PathVariable String id) {
        service.delete(id);
        return JsonResult.ok();
    }
}
