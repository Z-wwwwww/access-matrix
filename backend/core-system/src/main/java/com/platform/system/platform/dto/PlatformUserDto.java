package com.platform.system.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

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
            OffsetDateTime createTime
    ) {}

    /** Create body. The new user is provisioned in the {@code system} Keycloak realm. */
    public record CreateRequest(
            @NotBlank
            // Min length 3: Keycloak's default declarative user-profile enforces a
            // username length of 3..255, so a shorter name (e.g. "op") would be
            // rejected by KC with an opaque HTTP 400. Pre-validate it here for a
            // clean field error instead.
            @Size(min = 3, max = 64, message = "username must be 3-64 characters")
            @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,63}$",
                    message = "username must be lowercase alphanumeric / dash / underscore")
            String username,

            // Required: the realm marks email mandatory, so a missing email would
            // force an UPDATE_PROFILE step on the new user's first login.
            @NotBlank @Email @Size(max = 255) String email,

            @NotBlank @Size(max = 128) String displayName
    ) {}

    /**
     * Update body. Username is the login identity and is immutable here — only
     * the email + display name can be corrected (to fix a typo without a
     * delete-and-recreate). Synced to both Keycloak and {@code core_auth_user}.
     */
    public record UpdateRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 128) String displayName
    ) {}

    /**
     * Create response (plan B): the new user has NO temp password — they set their
     * own via the emailed invite link, same as resend. {@code emailSent} tells the
     * UI whether the invite mail went out (false → operator should use "resend").
     */
    public record CreateResponse(
            String id,
            String username,
            boolean emailSent
    ) {}

    /**
     * Reset / re-issue-credentials response — one-time temp password (KC forces a
     * change on next login) + whether the credentials email was dispatched. The
     * single "re-issue" path covers both "forgot password" and "resend / wrong
     * email": it always rotates the temp password and best-effort emails it.
     */
    public record ResetPwResponse(
            String username,
            String tempPassword,
            boolean emailSent
    ) {}
}
