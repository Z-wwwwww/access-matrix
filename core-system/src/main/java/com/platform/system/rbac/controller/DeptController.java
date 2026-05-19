package com.platform.system.rbac.controller;

import com.platform.system.rbac.dto.DeptNode;
import com.platform.system.rbac.service.DeptQueryService;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dept")
public class DeptController {

    private final DeptQueryService deptQueryService;

    public DeptController(DeptQueryService deptQueryService) {
        this.deptQueryService = deptQueryService;
    }

    /** Whole tenant's department tree. Requires {@code dept:read}. */
    @GetMapping("/tree")
    @RequiresPermission("dept:read")
    public JsonResult<List<DeptNode>> tree() {
        String tenantId = RequestContext.tenantId();
        return JsonResult.ok(deptQueryService.loadTree(tenantId == null ? "default" : tenantId));
    }
}
