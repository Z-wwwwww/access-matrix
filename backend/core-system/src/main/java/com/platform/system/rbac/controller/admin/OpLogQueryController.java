package com.platform.system.rbac.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.system.rbac.entity.OpLogEntity;
import com.platform.system.rbac.mapper.OpLogMapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/oplog")
public class OpLogQueryController {

    private final OpLogMapper mapper;

    public OpLogQueryController(OpLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/list")
    @RequiresPermission("oplog:read")
    public JsonResult<PageResult<OpLogEntity>> list(
            @RequestParam(defaultValue = "1")  long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Boolean success) {
        QueryWrapper<OpLogEntity> w = new QueryWrapper<OpLogEntity>().orderByDesc("create_time");
        if (module != null && !module.isBlank())          w.eq("module", module);
        if (action != null && !action.isBlank())          w.eq("action", action);
        if (userId != null && !userId.isBlank())          w.eq("user_id", userId);
        if (targetType != null && !targetType.isBlank())  w.eq("target_type", targetType);
        if (targetId != null && !targetId.isBlank())      w.eq("target_id", targetId);
        if (success != null)                               w.eq("success", success);

        Page<OpLogEntity> result = mapper.selectPage(new Page<>(page, size), w);
        return JsonResult.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @GetMapping("/{id}")
    @RequiresPermission("oplog:read")
    public JsonResult<OpLogEntity> get(@PathVariable String id) {
        OpLogEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException(ErrorCode.NOT_FOUND, "OpLog not found");
        return JsonResult.ok(e);
    }
}
