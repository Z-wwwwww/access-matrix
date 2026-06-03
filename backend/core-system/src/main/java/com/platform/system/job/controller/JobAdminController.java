package com.platform.system.job.controller;

import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.job.dto.JobDto;
import com.platform.system.job.dto.JobLogDto;
import com.platform.system.job.security.JobPermissions;
import com.platform.system.job.service.JobAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定時任務管理エンドポイント。システムドメインの書き込み規約に従い {@code /admin/job/...}。
 * ジョブの新規作成エンドポイントは存在しない（ジョブはコード側にしか定義できない）。
 */
@RestController
@RequestMapping("/admin/job")
public class JobAdminController {

    private final JobAdminService service;

    public JobAdminController(JobAdminService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @RequiresPermission(JobPermissions.JOB_READ)
    public JsonResult<PageResult<JobDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return JsonResult.ok(service.list(page, size, keyword));
    }

    @GetMapping("/log/list")
    @RequiresPermission(JobPermissions.JOB_READ)
    public JsonResult<PageResult<JobLogDto.View>> logs(
            @RequestParam(required = false) String jobCode,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return JsonResult.ok(service.logs(jobCode, page, size));
    }

    @PutMapping("/{id}")
    @RequiresPermission(JobPermissions.JOB_CONFIG)
    @OpLog(module = "job", action = "job.config", targetType = "job")
    public JsonResult<Void> update(@PathVariable String id, @Valid @RequestBody JobDto.UpdateRequest req) {
        service.update(id, req);
        return JsonResult.ok();
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission(JobPermissions.JOB_TOGGLE)
    @OpLog(module = "job", action = "job.enable", targetType = "job")
    public JsonResult<Void> enable(@PathVariable String id) {
        service.setEnabled(id, true);
        return JsonResult.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission(JobPermissions.JOB_TOGGLE)
    @OpLog(module = "job", action = "job.disable", targetType = "job")
    public JsonResult<Void> disable(@PathVariable String id) {
        service.setEnabled(id, false);
        return JsonResult.ok();
    }

    @PostMapping("/{id}/run")
    @RequiresPermission(JobPermissions.JOB_RUN)
    @OpLog(module = "job", action = "job.run", targetType = "job")
    public JsonResult<Void> run(@PathVariable String id) {
        service.runNow(id);
        return JsonResult.ok();
    }
}
