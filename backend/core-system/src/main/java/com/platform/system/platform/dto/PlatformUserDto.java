package com.platform.system.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * DTO container for the platform-ops user console ({@code /platform/users}):
 * manage the staff accounts that live in the {@code system} tenant and hold the
 * PLATFORM_ADMIN role.
 */
public final class PlatformUserDto {

    private PlatformUserDto() {}

    /** List row. */
    public record View(
            String id,
            String username,
            String email,
            String displayName,
            Integer status,            // 1 enabled / 0 disabled
            boolean platformAdmin,     // bound to the PLATFORM_ADMIN role
            LocalDateTime createTime
    ) {}

    /** Create body. The new user is provisioned in the {@code system} Keycloak realm. */
    public record CreateRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,63}$",
                    message = "username must be lowercase alphanumeric / dash / underscore")
            String username,

            // Required: the realm marks email mandatory, so a missing email would
            // force an UPDATE_PROFILE step on the new user's first login.
            @NotBlank @Email @Size(max = 255) String email,

            @NotBlank @Size(max = 128) String displayName
    ) {}

    /**
     * Create response. The {@code tempPassword} is a one-time temporary password
     * set on the Keycloak user (KC forces a change on first login). Shown once to
     * the operator who must hand it over securely — it is never stored or
     * retrievable afterwards.
     */
    public record CreateResponse(
            String id,
            String username,
            String tempPassword
    ) {}

    /** Reset-password response — one-time temp password (KC forces change on next login). */
    public record ResetPwResponse(
            String username,
            String tempPassword
    ) {}
}
