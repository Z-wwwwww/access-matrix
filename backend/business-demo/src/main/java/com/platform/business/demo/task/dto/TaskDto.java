package com.platform.business.demo.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class TaskDto {

    private TaskDto() {}

    /**
     * Width of every ULID foreign key in the schema ({@code character(26)}).
     * {@code dept_id} and {@code assignee_user_id} are written straight onto the
     * entity with no existence check, so an over-long value is not a clean
     * "not found" — Postgres rejects the write ("value too long for type
     * character(26)") and the caller gets an opaque 500. {@code deptId} used to
     * be capped at 64, which let 27–64-char values through to that 500;
     * {@code assigneeUserId} had no cap at all.
     */
    private static final int ULID_LEN = 26;

    public record CreateRequest(
            @NotBlank @Size(max = ULID_LEN) String deptId,
            @NotBlank @Size(max = 256) String title,
            @Size(max = 2048) String content,
            @NotNull Integer status,      // 1=TODO 2=DOING 3=DONE 4=CANCEL
            @NotNull Integer priority,    // 1=LOW 2=MID 3=HIGH
            @Size(max = ULID_LEN) String assigneeUserId,
            LocalDate dueDate) {}

    public record UpdateRequest(
            @Size(max = ULID_LEN) String deptId,
            @Size(max = 256) String title,
            @Size(max = 2048) String content,
            Integer status,
            Integer priority,
            @Size(max = ULID_LEN) String assigneeUserId,
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
            OffsetDateTime createTime,
            OffsetDateTime updateTime) {}
}
