package com.platform.system.platform.controller;

import com.platform.core.common.audit.OpLog;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.platform.dto.EventDto;
import com.platform.system.platform.service.EventAdminService;
import com.platform.system.security.PlatformPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-ops domain-event console: browse the {@code core_domain_event} outbox
 * and redrive failed events. Read is gated by {@code platform:event:read},
 * redrive by {@code platform:event:redrive} — both platform-namespace, so only
 * PLATFORM_ADMIN reaches here (business {@code tenant:*} does not match
 * {@code platform:*}). Mounted under {@code /platform} like the other ops
 * surfaces.
 */
@RestController
@RequestMapping("/platform/events")
public class EventAdminController {

    private final EventAdminService eventService;

    public EventAdminController(EventAdminService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @RequiresPermission(PlatformPermissions.EVENT_READ)
    public JsonResult<PageResult<EventDto.View>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer dispatchState,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String keyword) {
        return JsonResult.ok(eventService.list(page, size, dispatchState, eventType, aggregateType, keyword));
    }

    @GetMapping("/{id}")
    @RequiresPermission(PlatformPermissions.EVENT_READ)
    public JsonResult<EventDto.Detail> get(@PathVariable String id) {
        return JsonResult.ok(eventService.get(id));
    }

    /** Reset one failed event back to pending so the dispatcher retries it. */
    @PostMapping("/{id}/redrive")
    @RequiresPermission(PlatformPermissions.EVENT_REDRIVE)
    @OpLog(module = "platform", action = "event.redrive", targetType = "domainEvent")
    public JsonResult<Void> redrive(@PathVariable String id) {
        eventService.redrive(id);
        return JsonResult.ok();
    }

    /** Reset ALL failed events back to pending. Returns the number reset. */
    @PostMapping("/redrive-failed")
    @RequiresPermission(PlatformPermissions.EVENT_REDRIVE)
    @OpLog(module = "platform", action = "event.redriveAll", targetType = "domainEvent")
    public JsonResult<Integer> redriveAllFailed() {
        return JsonResult.ok(eventService.redriveAllFailed());
    }
}
