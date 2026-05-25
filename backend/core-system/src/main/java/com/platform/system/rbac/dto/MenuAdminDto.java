package com.platform.system.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public final class MenuAdminDto {

    private MenuAdminDto() {}

    public record CreateRequest(
            String parentId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String title,
            Map<String, String> titleI18n,
            @NotNull Integer menuType,
            @Size(max = 255) String path,
            @Size(max = 255) String component,
            @Size(max = 64) String icon,
            Integer sortOrder,
            Integer hide,
            Integer hideFooter,
            Integer hideSidebar,
            Integer pinned,
            @Size(max = 64) String tabUnique,
            @Size(max = 255) String redirect,
            @Size(max = 128) String permissionCode,
            Integer status) {}

    public record UpdateRequest(
            String parentId,
            @Size(max = 128) String title,
            Map<String, String> titleI18n,
            Integer menuType,
            @Size(max = 255) String path,
            @Size(max = 255) String component,
            @Size(max = 64) String icon,
            Integer sortOrder,
            Integer hide,
            Integer hideFooter,
            Integer hideSidebar,
            Integer pinned,
            @Size(max = 64) String tabUnique,
            @Size(max = 255) String redirect,
            @Size(max = 128) String permissionCode,
            Integer status) {}

    public record View(
            String id,
            String parentId,
            String code,
            String title,
            Map<String, String> titleI18n,
            Integer menuType,
            String path,
            String component,
            String icon,
            Integer sortOrder,
            Integer hide,
            Integer hideFooter,
            Integer hideSidebar,
            Integer pinned,
            String tabUnique,
            String redirect,
            String permissionCode,
            Integer status) {}
}
