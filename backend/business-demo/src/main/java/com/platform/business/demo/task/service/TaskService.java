package com.platform.business.demo.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.business.demo.task.dto.TaskDto;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.business.demo.task.mapper.TaskMapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.security.rbac.DataScopeDecision;
import com.platform.core.infrastructure.security.rbac.DataScopeHelper;
import com.platform.core.infrastructure.security.rbac.DataScopeResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final DataScopeResolver dataScopeResolver;

    public TaskService(TaskMapper taskMapper, DataScopeResolver dataScopeResolver) {
        this.taskMapper = taskMapper;
        this.dataScopeResolver = dataScopeResolver;
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
        if (req.assigneeUserId() != null) t.setAssigneeUserId(req.assigneeUserId());
        if (req.dueDate() != null) t.setDueDate(req.dueDate());
        taskMapper.updateById(t);
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
