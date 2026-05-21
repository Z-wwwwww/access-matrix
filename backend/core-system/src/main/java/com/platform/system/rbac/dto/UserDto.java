package com.platform.system.rbac.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class UserDto {

    private UserDto() {}

    public record CreateRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Email @Size(max = 255) String email,
            @Size(max = 32) String userNo,
            @Size(max = 128) String displayName,
            String deptId,
            Integer status) {}

    public record UpdateRequest(
            @Email @Size(max = 255) String email,
            @Size(max = 32) String userNo,
            @Size(max = 128) String displayName,
            String deptId,
            Integer status) {}

    public record View(
            String id,
            String username,
            String email,
            String userNo,
            String displayName,
            String deptId,
            Integer status) {}

    public record AssignRolesRequest(@NotNull List<String> roleIds) {}

    public record ChangeDeptRequest(String deptId) {}

    public record ChangeStatusRequest(@NotNull Integer status) {}
}
