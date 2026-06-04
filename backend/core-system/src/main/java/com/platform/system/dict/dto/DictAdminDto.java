package com.platform.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Admin CRUD shapes for managed dictionaries ({@code /admin/dict/**}).
 * Built-in (enum) dictionaries are NOT manageable here — they are code.
 */
public final class DictAdminDto {

    private DictAdminDto() {}

    // ── dict type ────────────────────────────────────────────────────

    public record TypeView(
            String id,
            String dictCode,
            Map<String, String> nameI18n,
            Integer builtin,
            String remark,
            Integer itemCount) {}

    public record TypeCreateRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "[a-z][a-z0-9_]*", message = "dictCode must be lower_snake_case")
            String dictCode,
            Map<String, String> nameI18n,
            @Size(max = 255) String remark) {}

    public record TypeUpdateRequest(
            Map<String, String> nameI18n,
            @Size(max = 255) String remark) {}

    // ── dict item ────────────────────────────────────────────────────

    public record ItemView(
            String id,
            String dictCode,
            String itemValue,
            Map<String, String> labelI18n,
            Integer sortNo,
            String cssClass,
            Integer status) {}

    public record ItemCreateRequest(
            @NotBlank @Size(max = 64) String itemValue,
            Map<String, String> labelI18n,
            Integer sortNo,
            @Size(max = 64) String cssClass,
            Integer status) {}

    /** {@code itemValue} is intentionally absent — values are frozen once created. */
    public record ItemUpdateRequest(
            Map<String, String> labelI18n,
            Integer sortNo,
            @Size(max = 64) String cssClass,
            Integer status) {}
}
