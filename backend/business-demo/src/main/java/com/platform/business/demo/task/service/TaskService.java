package com.platform.business.demo.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.business.demo.task.dto.TaskDto;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.business.demo.task.mapper.TaskMapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.notification.NotificationEvent;
import com.platform.core.common.notification.NotificationResolvedEvent;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.security.rbac.DataScopeDecision;
import com.platform.core.infrastructure.security.rbac.DataScopeHelper;
import com.platform.core.infrastructure.security.rbac.DataScopeResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final DataScopeResolver dataScopeResolver;
    private final ApplicationEventPublisher publisher;

    public TaskService(TaskMapper taskMapper, DataScopeResolver dataScopeResolver,
                       ApplicationEventPublisher publisher) {
        this.taskMapper = taskMapper;
        this.dataScopeResolver = dataScopeResolver;
        this.publisher = publisher;
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
        TaskEntity t = taskMapper.selectById(id);
        if (t == null || t.getMark() == null || t.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + id);
        }
        return toView(t);
    }

    @Transactional
    public String create(TaskDto.CreateRequest req) {
        TaskEntity t = new TaskEntity();
        t.setId(IdGenerator.ulid());
        t.setDeptId(req.deptId());
        t.setTitle(req.title());
        t.setContent(req.content());
        t.setStatus(req.status());
        t.setPriority(req.priority());
        t.setAssigneeUserId(req.assigneeUserId());
        t.setDueDate(req.dueDate());
        taskMapper.insert(t);
        notifyAssignee(t);
        return t.getId();
    }

    @Transactional
    public void update(String id, TaskDto.UpdateRequest req) {
        TaskEntity t = taskMapper.selectById(id);
        if (t == null || t.getMark() == null || t.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + id);
        }
        if (req.deptId() != null && !req.deptId().isBlank()) t.setDeptId(req.deptId());
        if (req.title() != null) t.setTitle(req.title());
        if (req.content() != null) t.setContent(req.content());
        if (req.status() != null) t.setStatus(req.status());
        if (req.priority() != null) t.setPriority(req.priority());
        boolean assigneeChanged = req.assigneeUserId() != null
                && !req.assigneeUserId().equals(t.getAssigneeUserId());
        if (req.assigneeUserId() != null) t.setAssigneeUserId(req.assigneeUserId());
        if (req.dueDate() != null) t.setDueDate(req.dueDate());
        taskMapper.updateById(t);
        if (assigneeChanged) notifyAssignee(t);
        // 「処理完了」= 完了(3)/取消(4)。この時点で該当タスクの action 通知を既読化する。
        if (req.status() != null && (req.status() == 3 || req.status() == 4)) {
            publisher.publishEvent(new NotificationResolvedEvent(
                    RequestContext.tenantIdOrDefault(), "demo_task", id));
        }
    }

    @Transactional
    public void delete(String id) {
        TaskEntity t = taskMapper.selectById(id);
        if (t == null || t.getMark() == null || t.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + id);
        }
        // mark は @TableLogic — BaseMapper.updateById では SET 句から除外されるので UpdateWrapper で明示。
        taskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TaskEntity>()
                        .eq("id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
    }

    private TaskDto.View toView(TaskEntity t) {
        return new TaskDto.View(
                t.getId(), t.getDeptId(), t.getTitle(), t.getContent(),
                t.getStatus(), t.getPriority(), t.getAssigneeUserId(), t.getDueDate(),
                t.getCreateUser(), t.getCreateTime(), t.getUpdateTime());
    }
}
