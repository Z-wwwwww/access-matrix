package com.platform.system.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * DTO container for the platform tenant management API. All records are
 * kept here so platform-ops endpoints have a single source of truth for
 * request / response shape.
 */
public final class TenantDto {

    private TenantDto() {}

    /** Read shape returned by {@code GET /platform/tenants}. */
    public record View(
            String id,
            String tenantCode,
            String displayName,
            String contactEmail,
            Integer status,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {}

    /**
     * Create body. The {@code tenantCode} is what becomes both the
     * Keycloak realm name and the tenant_id on every business row;
     * once chosen it's effectively immutable (rename is not supported).
     */
    public record CreateRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
                    message = "tenantCode must be a lowercase RFC1035 label")
            String tenantCode,

            @NotBlank @Size(max = 128) String displayName,

            @Email @Size(max = 255) String contactEmail
    ) {}
}
