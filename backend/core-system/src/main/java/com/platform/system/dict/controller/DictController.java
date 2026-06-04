package com.platform.system.dict.controller;

import com.platform.core.common.result.JsonResult;
import com.platform.system.dict.dto.DictReadDto;
import com.platform.system.dict.service.DictQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dictionary read endpoint — feeds frontend dropdowns. Authenticated but NOT
 * permission-gated: any logged-in user needs dict options to render forms/filters
 * (same posture as {@code /menu/me}). Hence it is in ArchitectureTest's
 * PUBLIC_CONTROLLERS allowlist (JWT-is-auth, no {@code @RequiresPermission}).
 */
@RestController
@RequestMapping("/dict")
public class DictController {

    private final DictQueryService service;

    public DictController(DictQueryService service) {
        this.service = service;
    }

    @GetMapping("/{code}")
    public JsonResult<DictReadDto.View> get(@PathVariable String code) {
        return JsonResult.ok(service.read(code));
    }
}
