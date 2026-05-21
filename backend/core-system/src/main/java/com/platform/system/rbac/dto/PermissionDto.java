package com.platform.system.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PermissionDto {

    private PermissionDto() {}

    public record CreateRequest(
            @NotBlank @Size(max = 128) String code,
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 64) String resource,
            @NotBlank @Size(max = 64) String action,
            @Size(max = 32)  String module,
            @Size(max = 512) String description) {}

    public record UpdateRequest(
            @Size(max = 128) String name,
            @Size(max = 32)  String module,
            @Size(max = 512) String description) {}

    public record View(
            String id,
            String code,
            String name,
            String resource,
            String action,
            String module,
            String description,
            Integer isBuiltIn) {}
}
