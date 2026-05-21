package com.platform.business.demo.task.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Demo task — the entity the data-scope walkthrough operates on.
 * {@code dept_id} drives DEPT / DEPT_AND_SUB / CUSTOM filtering;
 * {@code create_user} (inherited from {@link BaseEntity}) drives SELF.
 */
@Getter
@Setter
@TableName("demo_task")
public class TaskEntity extends BaseEntity {

    @TableField("dept_id")
    private String deptId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    /** 1=TODO 2=DOING 3=DONE 4=CANCEL */
    @TableField("status")
    private Integer status;

    /** 1=LOW 2=MID 3=HIGH */
    @TableField("priority")
    private Integer priority;

    @TableField("assignee_user_id")
    private String assigneeUserId;

    @TableField("due_date")
    private LocalDate dueDate;
}
