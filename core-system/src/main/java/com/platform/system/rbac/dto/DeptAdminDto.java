package com.platform.system.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DeptAdminDto {

    private DeptAdminDto() {}

    public record CreateRequest(
            String parentId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            Integer sortOrder,
            String leaderUserId,
            Integer status) {}

    public record UpdateRequest(
            String parentId,
            @Size(max = 128) String name,
            Integer sortOrder,
            String leaderUserId,
            Integer status) {}
}
