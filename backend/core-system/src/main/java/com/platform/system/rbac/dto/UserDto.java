package com.platform.system.rbac.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class UserDto {

    private UserDto() {}

    /**
     * Provisioning mode chosen by the admin on the user-create form.
     *
     * <p>DIRECT: admin sets an initial password (typed in the form); the
     * backend creates the Keycloak user with that password marked temporary,
     * and emails the user a welcome notice containing the credentials. The
     * user is forced to change the password on first login.
     *
     * <p>INVITE: admin does NOT set a password. The backend creates the
     * Keycloak user with no credentials, mints a single-use invite token,
     * and emails the user an "activate your account" link. The user sets
     * their own permanent password via the landing page.
     *
     * <p>INVITE is preferred for real users (better UX, admin never knows
     * the password). DIRECT is convenient for bots / shared accounts and
     * when SMTP is down.
     */
    public enum ProvisionMode { DIRECT, INVITE }

    /**
     * Width of every ULID foreign key in the schema ({@code character(26)}).
     * Reference ids that arrive in a request body must be capped at this, or
     * Postgres rejects the write outright ("value too long for type character
     * (26)") and the caller gets an opaque 500 instead of a field error —
     * {@code dept_id} is set straight onto the entity with no existence check
     * (unlike menu / dept {@code parentId}, which go through a
     * {@code require(...)} lookup and therefore 404 cleanly on garbage).
     */
    private static final int ULID_LEN = 26;

    // userNo は採番（NumberingService.next("USER", ...)）で自動付番。
    // 旧クライアント互換のためフィールドが届いても無視する（DTO に持たない＝Jackson が黙って捨てる）。
    public record CreateRequest(
            @NotBlank @Size(max = 64) String username,
            // Optional at the bean level (INVITE mode sends none / an empty string).
            // DIRECT mode validates presence + length/complexity in the service via
            // PasswordPolicyService — so we only cap the max here, never require a min
            // (a min=8 here would wrongly reject INVITE's empty password with a 701).
            @Size(max = 128) String password,
            @Email @Size(max = 255) String email,
            @Size(max = 128) String displayName,
            @Size(max = ULID_LEN) String deptId,
            Integer status,
            // Defaults to INVITE when omitted (prefer better-UX path).
            ProvisionMode mode) {

        public CreateRequest {
            if (mode == null) mode = ProvisionMode.INVITE;
        }
    }

    public record UpdateRequest(
            @Email @Size(max = 255) String email,
            @Size(max = 128) String displayName,
            @Size(max = ULID_LEN) String deptId,
            Integer status) {}

    /**
     * Self-service profile edit (the Profile page). Contact fields only —
     * a user can never change their own dept / status / roles this way; those
     * are admin-only and the admin console additionally refuses self-edits.
     */
    public record ProfileUpdateRequest(
            @Email @Size(max = 255) String email,
            @Size(max = 128) String displayName) {}

    public record View(
            String id,
            String username,
            String email,
            String userNo,
            String displayName,
            String deptId,
            Integer status,
            // True for the platform's built-in admin row (read-only at the
            // admin API — only contact fields are editable). The frontend
            // uses this to lock structural fields, instead of hardcoding the
            // built-in username (which drifted: 'admin' vs 'demo-admin').
            boolean builtin,
            // True when the user holds the tenant's singular SUPER_ADMIN role.
            // The frontend uses it to lock structural fields (only email /
            // display name editable) and to hide the force-logout / suspend /
            // delete actions; the backend enforces the same on every path.
            boolean superAdmin) {}

    public record AssignRolesRequest(@NotNull List<String> roleIds) {}

    /**
     * One-time response of the admin password reset: the generated temporary
     * password is shown ONCE to the admin (never persisted / re-readable).
     * {@code emailSent} tells the UI whether the user was also notified by mail.
     */
    public record ResetPwResponse(String username, String tempPassword, boolean emailSent) {}

    public record ChangeDeptRequest(@Size(max = ULID_LEN) String deptId) {}

    public record ChangeStatusRequest(@NotNull Integer status) {}
}
