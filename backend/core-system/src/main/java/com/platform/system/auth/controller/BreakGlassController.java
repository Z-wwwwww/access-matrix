package com.platform.system.auth.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.core.common.security.RequiresPermission;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Self-service break-glass credential management for super-admin users.
 *
 * <h3>What is the break-glass password?</h3>
 * <p>In OIDC mode, regular users authenticate via Keycloak — their daily
 * password lives in KC and we never store it locally. But if Keycloak
 * becomes unavailable, the system needs <em>some</em> way for an
 * administrator to log in and intervene. {@code core_auth_user.password_hash}
 * still exists for exactly this purpose, and {@code DualModeJwtDecoder}
 * still accepts HS256 tokens signed by the legacy {@code /auth/login}
 * path that validates against that hash.
 *
 * <p>This is the "break-glass" credential — a separate, independently-
 * managed password used <em>only</em> when SSO is unreachable. It is
 * different from the user's daily SSO password (which lives in
 * Keycloak); they are not synced and they should NOT be the same.
 *
 * <h3>Who gets one</h3>
 * Only users holding the SUPER_ADMIN role. The {@code OidcJitUserService}
 * bind path explicitly preserves their {@code password_hash} during SSO
 * migration; for everyone else the hash is cleared on first SSO login,
 * making this endpoint a no-op (and explicitly rejected — see role guard
 * in {@link #setBreakGlassPassword}).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /me/break-glass-password/status} — read whether the
 *       caller currently has a configured break-glass password. Used by
 *       the UI to render "you have one / you don't" + a rotate-now CTA.</li>
 *   <li>{@code POST /me/break-glass-password} — set a new break-glass
 *       password for the caller. Subject to {@link PasswordPolicyService}
 *       (length, complexity, HIBP-pwned check) just like a regular
 *       password. Audit-logged via {@code @OpLog} so a compromised admin
 *       session can't silently rotate the credential without a trace.</li>
 * </ul>
 *
 * <h3>Why no "change someone else's break-glass password"</h3>
 * Self-service only — admin A cannot set admin B's break-glass password
 * via this endpoint. If admin B forgot theirs and SSO is reachable, they
 * can call this endpoint themselves. If SSO is down AND they forgot,
 * the operator must use a SQL UPDATE as last resort (documented in
 * docs/break-glass.md). Centralising on self-service keeps the
 * audit trail clean (the row's {@code update_user} is always the same
 * person as the {@code user_id}).
 */
@RestController
@RequestMapping("/me/break-glass-password")
public class BreakGlassController {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassController.class);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder encoder;
    private final PasswordPolicyService passwordPolicy;

    public BreakGlassController(UserMapper userMapper,
                                RoleMapper roleMapper,
                                PasswordEncoder encoder,
                                PasswordPolicyService passwordPolicy) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * Whether the caller has a break-glass password configured. Used by
     * the dialog to show "Configured" vs "Not configured" + a CTA. No
     * sensitive data returned (never the hash itself, never a timestamp
     * an attacker could correlate with leaked credentials).
     */
    @GetMapping("/status")
    @RequiresPermission("*:*")    // super-admin only
    public JsonResult<Map<String, Object>> status() {
        String userId = requireSuperAdmin();
        UserEntity row = userMapper.selectById(userId);
        boolean configured = row != null
                && row.getPasswordHash() != null
                && !row.getPasswordHash().isBlank();
        return JsonResult.ok(Map.of(
                "configured", configured
        ));
    }

    /**
     * Set / rotate the caller's break-glass password. The caller MUST be
     * a super-admin (enforced both via {@code @RequiresPermission("*:*")}
     * AND a defence-in-depth role check, since "*:*" is the catch-all
     * permission grant — checking the actual role binding makes the
     * intent explicit and survives a future permission re-org).
     */
    @PostMapping
    @RequiresPermission("*:*")
    @OpLog(module = "system", action = "auth.breakGlassSet", targetType = "user")
    public JsonResult<Void> setBreakGlassPassword(@Valid @RequestBody SetBreakGlassRequest req) {
        String userId = requireSuperAdmin();
        String tenantId = RequestContext.tenantIdOrDefault();

        // Same policy gate as every other password write. HIBP is on by
        // default (see application.yml app.security.password-policy.hibp-enabled);
        // an admin reusing a common password gets a 400 before we hash.
        passwordPolicy.validate(req.password());

        String hash = encoder.encode(req.password());
        userMapper.update(null,
                new UpdateWrapper<UserEntity>()
                        .eq("id", userId)
                        .eq("tenant_id", tenantId)
                        .set("password_hash", hash)
                        .set("update_user", "self-break-glass-set")
                        .set("update_time", LocalDateTime.now()));

        log.info("[break-glass] super-admin {} (tenant {}) rotated their break-glass password",
                userId, tenantId);
        return JsonResult.ok();
    }

    /**
     * Resolve the current request's user id and verify they hold the
     * SUPER_ADMIN role. Returns the user id on success, throws otherwise.
     * The role check is the authoritative gate; {@code @RequiresPermission("*:*")}
     * is a catch-all that future re-orgs might widen, so we don't rely on
     * it alone for this security-relevant endpoint.
     */
    private String requireSuperAdmin() {
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Not authenticated");
        }
        String tenantId = RequestContext.tenantIdOrDefault();
        boolean isSuperAdmin;
        try {
            isSuperAdmin = roleMapper.findRoleIdsByUserId(userId, tenantId)
                    .contains(BuiltInRoles.SUPER_ADMIN_ID);
        } catch (Exception e) {
            // Conservative: a transient role-lookup failure is treated as
            // "not super-admin" rather than "yes admin" — break-glass is
            // security-sensitive enough that a failed check should refuse,
            // not allow. Opposite default of the OidcJitUserService check,
            // which protects EXISTING super-admins from accidental hash
            // clearing — here we're guarding a write, not preserving state.
            log.warn("[break-glass] role-lookup failed for {} ({}) — refusing", userId, e.toString());
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Could not verify super-admin role; refusing for safety");
        }
        if (!isSuperAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Break-glass credentials are only available to super-admin users. "
                            + "Regular users authenticate exclusively via SSO; when SSO is down, please wait.");
        }
        return userId;
    }

    public record SetBreakGlassRequest(
            @NotBlank @Size(min = 8, max = 128) String password) {}
}
