package com.platform.system.rbac.controller.admin;

import com.platform.system.rbac.dto.MenuAdminDto;
import com.platform.system.rbac.service.MenuAdminService;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.security.PlatformPermissions;
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

    // READ is shared: business super (tenant:* → menu:read) needs it for the
    // Role-edit menu picker; platform admin (*:* → platform:menu:read) for the
    // management page. WRITE is platform-only (platform:menu:*).
    @GetMapping("/list")
    @RequiresPermission(anyOf = {SystemPermissions.MENU_READ, PlatformPermissions.MENU_READ})
    public JsonResult<List<MenuAdminDto.View>> list() {
        return JsonResult.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @RequiresPermission(anyOf = {SystemPermissions.MENU_READ, PlatformPermissions.MENU_READ})
    public JsonResult<MenuAdminDto.View> get(@PathVariable String id) {
        return JsonResult.ok(service.get(id));
    }

    @PostMapping
    @RequiresPermission(PlatformPermissions.MENU_CREATE)
    @OpLog(module = "platform", action = "menu.create", targetType = "menu")
    public JsonResult<String> create(@Valid @RequestBody MenuAdminDto.CreateRequest req) {
        return JsonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission(PlatformPermissions.MENU_UPDATE)
    @OpLog(module = "platform", action = "menu.update", targetType = "menu")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody MenuAdminDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(PlatformPermissions.MENU_DELETE)
    @OpLog(module = "platform", action = "menu.delete", targetType = "menu")
    public JsonResult<Void> delete(@PathVariable String id) {
        service.delete(id);
        return JsonResult.ok();
    }
}
