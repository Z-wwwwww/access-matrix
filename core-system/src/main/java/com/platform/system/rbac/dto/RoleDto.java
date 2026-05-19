package com.platform.system.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request / response DTOs for role admin endpoints. */
public final class RoleDto {

    private RoleDto() {}

    public record CreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            Integer dataScope,
            Integer sortOrder,
            Integer status) {}

    public record UpdateRequest(
            @Size(max = 128) String name,
            @Size(max = 512) String description,
            Integer dataScope,
            Integer sortOrder,
            Integer status) {}

    public record View(
            String id,
            String code,
            String name,
            String description,
            Integer dataScope,
            Integer isBuiltIn,
            Integer status,
            Integer sortOrder) {}

    public record BindIdsRequest(@NotNull List<String> ids) {}
}
