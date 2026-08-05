package com.platform.business.demo.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.business.demo.task.TaskStatus;
import com.platform.business.demo.task.dto.TaskDto;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.business.demo.task.mapper.TaskMapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ConcurrentEdit;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.notification.NotificationEvent;
import com.platform.core.common.notification.NotificationResolvedEvent;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.event.DomainEvent;
import com.platform.core.infrastructure.event.EventPublisher;
import com.platform.core.infrastructure.security.rbac.DataScopeDecision;
import com.platform.core.infrastructure.security.rbac.DataScopeHelper;
import com.platform.core.infrastructure.security.rbac.DataScopeResolver;
import com.platform.system.dict.service.DictQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final DataScopeResolver dataScopeResolver;
    private final ApplicationEventPublisher publisher;
    private final DictQueryService dictQueryService;
    /** Outbox publisher — writes a domain-event row into core_domain_event in the same tx. */
    private final EventPublisher events;

    public TaskService(TaskMapper taskMapper, DataScopeResolver dataScopeResolver,
                       ApplicationEventPublisher publisher, DictQueryService dictQueryService,
                       EventPublisher events) {
        this.taskMapper = taskMapper;
        this.dataScopeResolver = dataScopeResolver;
        this.publisher = publisher;
        this.dictQueryService = dictQueryService;
        this.events = events;
    }

    /**
     * Emit a {@code demo.task.*} domain event to the outbox. Demonstrates the
     * core_domain_event substrate end-to-end (publish → outbox → dispatcher →
     * platform event console). Called inside the @Transactional write so the
     * event commits atomically with the change — no event without the write.
     *
     * <p>This is demo-grade emission on plain CRUD; a real revenue-management
     * module should be more selective about what's event-worthy and register an
     * Events constants class + guard (mirrors the permission-code pattern).
     */
    private void emit(String eventType, TaskEntity t) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", t.getTitle());
        payload.put("status", t.getStatus());
        payload.put("priority", t.getPriority());
        payload.put("deptId", t.getDeptId());
        payload.put("assigneeUserId", t.getAssigneeUserId());
        events.publish(DomainEvent.of("DemoTask", t.getId(), eventType, payload));
    }

    /**
     * Form-3 validation: status / priority are managed dicts (open set, ops can add),
     * so validate against the live dict — NOT the {@link TaskStatus} enum (which only
     * covers the code-branched subset). Unknown values are rejected; ops-added ones pass.
     */
    private void validateDictValue(String code, Integer value, String field) {
        validateDictValue(code, value, field, null);
    }

    /**
     * @param current the value already stored on the row being edited, or null on
     *                create. A retired (status=0) option is refused for NEW input but
     *                carried forward unchanged, so editing an unrelated field on an
     *                old row never fails on a value the operator can no longer pick.
     */
    private void validateDictValue(String code, Integer value, String field, Integer current) {
        if (value == null) return;
        if (current != null && value.equals(current)) {
            // Unchanged — must stay acceptable even if the option was retired since.
            if (dictQueryService.isValidValue(code, value)) return;
        }
        // isSelectableValue, not isValidValue: DictAdminService.deleteItem refuses to
        // hard-delete a branch/referenced value and tells the operator to disable it
        // (status=0) instead, so status=0 IS the retirement mechanism. isValidValue
        // ignores status, so retiring an option used to change nothing server-side —
        // it only stopped fresh UI from offering it (useDict.options filters on
        // `enabled`), while any client, including a tab holding a pre-disable dict
        // cache, could still write the retired value.
        if (!dictQueryService.isSelectableValue(code, value)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.invalidValue");
        }
    }

    /**
     * Fire an in-app notification to the assignee. Published inside the
     * @Transactional method; the core listener runs AFTER_COMMIT (async), so a
     * rolled-back save never notifies. Business stays decoupled — it depends
     * only on {@link NotificationEvent} in core-common, not the notification
     * module.
     */
    private void notifyAssignee(TaskEntity t) {
        if (t.getAssigneeUserId() == null || t.getAssigneeUserId().isBlank()) return;
        // action 型:担当者が「対応すべき」もの。UI は「待処理」バッジで区別し、
        // タスクを完了/取消にすると NotificationResolvedEvent で既読になる。
        // link はクリーンな一覧ルート。詳細は bizType/bizId を手がかりにフロントが
        // ドロワーで開く(id は URL ではなく store 経由)。
        publisher.publishEvent(NotificationEvent.action(
                RequestContext.tenantIdOrDefault(), t.getAssigneeUserId(),
                "task.assigned", "対応が必要なタスクが割り当てられました", t.getTitle(),
                "/demo/task", "demo_task", t.getId()));
    }

    /**
     * List tasks visible to the current user. The {@link DataScopeHelper#apply}
     * call is the headline: it consults the caller's
     * {@link DataScopeDecision} and rewrites the wrapper with
     * {@code dept_id IN (...)} and/or {@code create_user = ?} so the SQL
     * itself is filtered. The {@link com.platform.core.common.security.DataScope}
     * annotation on {@link TaskMapper} causes
     * {@link com.platform.core.infrastructure.security.rbac.DataScopeAspect}
     * to reject the call (dev) if a caller forgot to invoke {@code apply}.
     */
    public PageResult<TaskDto.View> list(long page, long size, String keyword, Integer status) {
        Page<TaskEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<TaskEntity> w = new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getMark, 1)
                .orderByDesc(TaskEntity::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            w.like(TaskEntity::getTitle, keyword);
        }
        if (status != null) {
            w.eq(TaskEntity::getStatus, status);
        }

        DataScopeDecision decision = dataScopeResolver.currentDecision();
        DataScopeHelper.apply(w, decision, TaskEntity::getDeptId, TaskEntity::getCreateUser);

        Page<TaskEntity> result = taskMapper.selectPage(p, w);
        List<TaskDto.View> records = result.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public TaskDto.View get(String id) {
        TaskEntity t = loadVisibleOr404(id);
        return toView(t);
    }

    /**
     * Fetch a task by id, enforcing BOTH tenant scope (via the MyBatis tenant
     * interceptor on {@code selectById}) AND data scope (dept / self) on the
     * fetched row. {@code selectById} alone is tenant-scoped but NOT
     * data-scoped, so without the {@link DataScopeHelper#isVisible} gate a
     * DEPT/SELF-scoped caller could read or mutate any row in the tenant by id
     * (IDOR). Out-of-scope (and missing) both surface as NOT_FOUND so the
     * response never reveals that an id the caller may not see exists.
     */
    private TaskEntity loadVisibleOr404(String id) {
        TaskEntity t = taskMapper.selectById(id);
        if (t == null || t.getMark() == null || t.getMark() != 1
                || !DataScopeHelper.isVisible(dataScopeResolver.currentDecision(),
                        t.getDeptId(), t.getCreateUser())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + id);
        }
        return t;
    }

    @Transactional
    public String create(TaskDto.CreateRequest req) {
        TaskEntity t = new TaskEntity();
        t.setId(IdGenerator.ulid());
        t.setDeptId(req.deptId());
        t.setTitle(req.title());
        t.setContent(req.content());
        validateDictValue("task_status", req.status(), "status");
        validateDictValue("task_priority", req.priority(), "priority");
        t.setStatus(req.status());
        t.setPriority(req.priority());
        t.setAssigneeUserId(req.assigneeUserId());
        t.setDueDate(req.dueDate());
        taskMapper.insert(t);
        notifyAssignee(t);
        emit("demo.task.created", t);
        return t.getId();
    }

    @Transactional
    public void update(String id, TaskDto.UpdateRequest req) {
        TaskEntity t = loadVisibleOr404(id);
        if (req.deptId() != null && !req.deptId().isBlank()) t.setDeptId(req.deptId());
        if (req.title() != null) t.setTitle(req.title());
        if (req.content() != null) t.setContent(req.content());
        if (req.status() != null) {
            validateDictValue("task_status", req.status(), "status", t.getStatus());
            t.setStatus(req.status());
        }
        if (req.priority() != null) {
            validateDictValue("task_priority", req.priority(), "priority", t.getPriority());
            t.setPriority(req.priority());
        }
        boolean assigneeChanged = req.assigneeUserId() != null
                && !req.assigneeUserId().equals(t.getAssigneeUserId());
        if (req.assigneeUserId() != null) t.setAssigneeUserId(req.assigneeUserId());
        if (req.dueDate() != null) t.setDueDate(req.dueDate());
        ConcurrentEdit.requireApplied(taskMapper.updateById(t));
        emit("demo.task.updated", t);
        if (assigneeChanged) notifyAssignee(t);
        // 「処理完了」= 完了/取消。この時点で該当タスクの action 通知を既読化する。
        if (req.status() != null
                && (req.status() == TaskStatus.DONE.code() || req.status() == TaskStatus.CANCELLED.code())) {
            publisher.publishEvent(new NotificationResolvedEvent(
                    RequestContext.tenantIdOrDefault(), "demo_task", id));
        }
    }

    @Transactional
    public void delete(String id) {
        TaskEntity t = loadVisibleOr404(id);
        // mark は @TableLogic — BaseMapper.updateById では SET 句から除外されるので UpdateWrapper で明示。
        taskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TaskEntity>()
                        .eq("id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        emit("demo.task.deleted", t);
        // 削除も「このタスクについてもう対応することはない」— update() が 完了/取消 で
        // action 通知を既読化しているのと同じ理由で、削除でも既読化する。これが無いと
        // notifyAssignee() が作った未読の action 通知が永久に残り、担当者のベルには
        // 「対応が必要」なのに開くと 404 になる項目が居座り続ける（bizId は mark=0 の行を
        // 指しており、get(id) は loadVisibleOr404 で NOT_FOUND を返す）。
        publisher.publishEvent(new NotificationResolvedEvent(
                RequestContext.tenantIdOrDefault(), "demo_task", id));
    }

    private TaskDto.View toView(TaskEntity t) {
        return new TaskDto.View(
                t.getId(), t.getDeptId(), t.getTitle(), t.getContent(),
                t.getStatus(), t.getPriority(), t.getAssigneeUserId(), t.getDueDate(),
                t.getCreateUser(), t.getCreateTime(), t.getUpdateTime());
    }
}
