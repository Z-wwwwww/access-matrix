package com.platform.business.demo.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TaskDto {

    private TaskDto() {}

    public record CreateRequest(
            @NotBlank @Size(max = 64) String deptId,
            @NotBlank @Size(max = 256) String title,
            @Size(max = 2048) String content,
            @NotNull Integer status,      // 1=TODO 2=DOING 3=DONE 4=CANCEL
            @NotNull Integer priority,    // 1=LOW 2=MID 3=HIGH
            String assigneeUserId,
            LocalDate dueDate) {}

    public record UpdateRequest(
            @Size(max = 64) String deptId,
            @Size(max = 256) String title,
            @Size(max = 2048) String content,
            Integer status,
            Integer priority,
            String assigneeUserId,
            LocalDate dueDate) {}

    /**
     * Read view returned by {@code /demo/task/list} and {@code /demo/task/{id}}.
     * {@code createUser} is the creator's user id — the front-end resolves it
     * to a label via the user-list endpoint (same pattern as Dept's leader column).
     */
    public record View(
            String id,
            String deptId,
            String title,
            String content,
            Integer status,
            Integer priority,
            String assigneeUserId,
            LocalDate dueDate,
            String createUser,
            LocalDateTime createTime,
            LocalDateTime updateTime) {}
}
