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
     *
     * <p>{@code adminUsername} is the username of the auto-provisioned
     * SUPER_ADMIN user. When blank, the server derives it from
     * {@code contactEmail}'s local-part (lowercased and sanitised to
     * {@code [a-z0-9_-]}); if that derivation yields an empty or
     * already-taken username, the server falls back to {@code "admin"}.
     * Operators wanting a specific username can override here. Whatever
     * username lands, the user is created with {@code password_hash=NULL}
     * and receives an invite mail at {@code contactEmail}.
     */
    public record CreateRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
                    message = "tenantCode must be a lowercase RFC1035 label")
            String tenantCode,

            @NotBlank @Size(max = 128) String displayName,

            @Email @Size(max = 255) String contactEmail,

            @Size(max = 64)
            @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,63}$|^$",
                    message = "adminUsername must be lowercase alphanumeric / dash / underscore")
            String adminUsername
    ) {}

    /**
     * Patch body for the registry row. tenant_code stays immutable
     * (renaming it would require coordinated changes across Keycloak realm,
     * every business row's tenant_id, and external clients) — anything else
     * about the tenant is fair game from the platform-ops console.
     */
    public record UpdateRequest(
            @NotBlank @Size(max = 128) String displayName,
            @Email @Size(max = 255) String contactEmail
    ) {}

    /**
     * Request body for {@code POST /platform/tenants/{id}/support-session}.
     * A non-blank {@code reason} is mandatory — it's the primary audit
     * justification and lands in {@code core_oplog.request_body} via the
     * {@code @OpLog} aspect.
     */
    public record SupportSessionRequest(
            @NotBlank @Size(max = 255) String reason
    ) {}

    /**
     * Response shape from starting a support session. The frontend stashes
     * {@code token} as its active Bearer (saving the prior ops token for
     * restoration on terminate) and renders the countdown / banner from
     * {@code expiresAt} / {@code expiresInSec}.
     */
    public record SupportSessionResponse(
            String token,
            String sessionId,
            String tenantCode,
            String displayName,
            String expiresAt,
            long expiresInSec
    ) {}
}
