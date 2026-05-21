package com.platform.system.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DeptAdminDto {

    private DeptAdminDto() {}

    /**
     * Create-department request.
     *
     * <p>{@code leaderUserId}: informational only — a memo for "who heads this
     * department" used by the front-end to display the leader's name. It does
     * <b>not</b> grant any permission and does <b>not</b> influence data scope.
     * Picked via a dropdown sourced from {@code GET /admin/user/list?keyword=...}.
     * The server validates that the supplied id resolves to an active user;
     * pass {@code null} or omit to leave the field empty.
     */
    public record CreateRequest(
            String parentId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            Integer sortOrder,
            String leaderUserId,
            Integer status) {}

    /**
     * Update-department request. See {@link CreateRequest} for the
     * {@code leaderUserId} contract — same rules apply (informational,
     * non-authorising, validated server-side, populated from
     * {@code /admin/user/list}).
     */
    public record UpdateRequest(
            String parentId,
            @Size(max = 128) String name,
            Integer sortOrder,
            String leaderUserId,
            Integer status) {}
}
