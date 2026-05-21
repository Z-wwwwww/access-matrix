package com.platform.business.demo.task.controller;

import com.platform.business.demo.task.dto.TaskDto;
import com.platform.business.demo.task.service.TaskService;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
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

/**
 * Demo task endpoints — under the business path prefix {@code /demo/...} so it
 * never collides with the system-domain {@code /admin/...} write paths.
 */
@RestController
@RequestMapping("/demo/task")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @RequiresPermission("task:read")
    public JsonResult<PageResult<TaskDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return JsonResult.ok(service.list(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    @RequiresPermission("task:read")
    public JsonResult<TaskDto.View> get(@PathVariable String id) {
        return JsonResult.ok(service.get(id));
    }

    @PostMapping
    @RequiresPermission("task:create")
    @OpLog(module = "demo", action = "task.create", targetType = "task")
    public JsonResult<String> create(@Valid @RequestBody TaskDto.CreateRequest req) {
        return JsonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequiresPermission("task:update")
    @OpLog(module = "demo", action = "task.update", targetType = "task")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody TaskDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("task:delete")
    @OpLog(module = "demo", action = "task.delete", targetType = "task")
    public JsonResult<Void> delete(@PathVariable String id) {
        service.delete(id);
        return JsonResult.ok();
    }
}
