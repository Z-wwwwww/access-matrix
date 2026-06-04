package com.platform.system.dict.controller.admin;

import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.dict.dto.DictAdminDto;
import com.platform.system.dict.service.DictAdminService;
import com.platform.system.security.PlatformPermissions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Managed-dictionary admin ({@code /admin/dict/**}) — platform-ops only
 * ({@code platform:dict:*}), mirroring the menu console (V41). Built-in (enum)
 * dictionaries are NOT manageable here.
 */
@RestController
@RequestMapping("/admin/dict")
public class DictAdminController {

    private final DictAdminService service;

    public DictAdminController(DictAdminService service) {
        this.service = service;
    }

    // ── dict types ───────────────────────────────────────────────────

    @GetMapping("/list")
    @RequiresPermission(PlatformPermissions.DICT_READ)
    public JsonResult<List<DictAdminDto.TypeView>> listTypes() {
        return JsonResult.ok(service.listTypes());
    }

    @PostMapping
    @RequiresPermission(PlatformPermissions.DICT_CREATE)
    @OpLog(module = "platform", action = "dict.create", targetType = "dict")
    public JsonResult<String> createType(@Valid @RequestBody DictAdminDto.TypeCreateRequest req) {
        return JsonResult.ok(service.createType(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission(PlatformPermissions.DICT_UPDATE)
    @OpLog(module = "platform", action = "dict.update", targetType = "dict")
    public JsonResult<Void> updateType(@PathVariable String id, @Valid @RequestBody DictAdminDto.TypeUpdateRequest req) {
        service.updateType(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(PlatformPermissions.DICT_DELETE)
    @OpLog(module = "platform", action = "dict.delete", targetType = "dict")
    public JsonResult<Void> deleteType(@PathVariable String id) {
        service.deleteType(id);
        return JsonResult.ok();
    }

    // ── dict items ───────────────────────────────────────────────────

    @GetMapping("/{code}/items")
    @RequiresPermission(PlatformPermissions.DICT_READ)
    public JsonResult<List<DictAdminDto.ItemView>> listItems(@PathVariable String code) {
        return JsonResult.ok(service.listItems(code));
    }

    @PostMapping("/{code}/item")
    @RequiresPermission(PlatformPermissions.DICT_CREATE)
    @OpLog(module = "platform", action = "dict.item.create", targetType = "dict")
    public JsonResult<String> createItem(@PathVariable String code, @Valid @RequestBody DictAdminDto.ItemCreateRequest req) {
        return JsonResult.ok(service.createItem(code, req));
    }

    @PutMapping("/item/{id}")
    @RequiresPermission(PlatformPermissions.DICT_UPDATE)
    @OpLog(module = "platform", action = "dict.item.update", targetType = "dict")
    public JsonResult<Void> updateItem(@PathVariable String id, @Valid @RequestBody DictAdminDto.ItemUpdateRequest req) {
        service.updateItem(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/item/{id}")
    @RequiresPermission(PlatformPermissions.DICT_DELETE)
    @OpLog(module = "platform", action = "dict.item.delete", targetType = "dict")
    public JsonResult<Void> deleteItem(@PathVariable String id) {
        service.deleteItem(id);
        return JsonResult.ok();
    }
}
