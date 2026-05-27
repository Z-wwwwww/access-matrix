package com.platform.system.auth.controller;

import com.platform.system.auth.dto.ResetPasswordRequest;
import com.platform.system.auth.dto.UnlockRequest;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.security.SystemPermissions;
import com.platform.core.infrastructure.security.AccountLockoutService;
import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final UserMapper userMapper;
    private final AccountLockoutService lockoutService;
    private final PasswordPolicyService passwordPolicy;
    private final PasswordEncoder encoder;
    private final ForceLogoutService forceLogoutService;

    /**
     * Active security mode. In OIDC mode this controller's reset-password
     * endpoint refuses — Keycloak owns user credentials, and writing the
     * local password_hash here would diverge from KC without any login
     * pathway picking the value up. Admins should use Keycloak's self-service
     * reset for SSO password recovery, and the break-glass endpoint
     * (/me/break-glass-password) for emergency credential management.
     */
    @Value("${app.security.mode:permit-all}")
    private String securityMode;

    public AdminAuthController(UserMapper userMapper, AccountLockoutService lockoutService,
                               PasswordPolicyService passwordPolicy, PasswordEncoder encoder,
                               ForceLogoutService forceLogoutService) {
        this.userMapper = userMapper;
        this.lockoutService = lockoutService;
        this.passwordPolicy = passwordPolicy;
        this.encoder = encoder;
        this.forceLogoutService = forceLogoutService;
    }

    @PostMapping("/unlock")
    @RequiresPermission(SystemPermissions.AUTH_UNLOCK)
    @OpLog(module = "system", action = "auth.unlock", targetType = "user")
    public JsonResult<Void> unlock(@Valid @RequestBody UnlockRequest body) {
        UserEntity user = userMapper.findByIdentifier(tenant(), body.username());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        lockoutService.reset(tenant(), body.username());
        user.setStatus(1);
        userMapper.updateById(user);
        return JsonResult.ok();
    }

    @PostMapping("/reset-password")
    @RequiresPermission(SystemPermissions.AUTH_RESET_PASSWORD)
    @OpLog(module = "system", action = "auth.resetPassword", targetType = "user")
    public JsonResult<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        // OIDC mode: this endpoint is a footgun. Writing local password_hash
        // never propagates to Keycloak and never gets picked up by the SSO
        // login path; worse, it can re-populate the column on a non-super-admin
        // row after the JIT bind path cleared it, undoing the "as if always
        // OIDC" data invariant. Refuse here as defense in depth — the SPA
        // already greys out the trigger, but a direct API call would otherwise
        // succeed silently.
        if ("oidc".equalsIgnoreCase(securityMode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Password reset is disabled in OIDC mode. "
                            + "Users reset their SSO password via Keycloak's account console "
                            + "(or the 'Forgot password' link on the login page). "
                            + "For break-glass credentials, see /me/break-glass-password.");
        }
        passwordPolicy.validate(body.newPassword());
        UserEntity user = userMapper.findByIdentifier(tenant(), body.username());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        user.setPasswordHash(encoder.encode(body.newPassword()));
        userMapper.updateById(user);
        // パスワード変更後は当該ユーザーの既存トークンを必ず無効化する。
        // これをしないと、漏洩した旧 access token が自然失効までずっと使えてしまい、
        // 「リセット = 攻撃者を追い出す」という前提が成り立たない。
        forceLogoutService.kickOut(user.getId());
        return JsonResult.ok();
    }

    private static String tenant() {
        String tid = RequestContext.tenantId();
        return (tid == null || tid.isBlank()) ? "default" : tid;
    }

    /**
     * Force-logout a user — every in-flight access token issued <em>before</em>
     * this call will be rejected by the permission aspect at next API hit.
     * Requires the {@code *:*} super-permission so a kicked-out admin can't
     * grant the kick-back via a low-tier permission.
     */
    @PostMapping("/force-logout/{userId}")
    @RequiresPermission("*:*")
    @OpLog(module = "system", action = "auth.forceLogout", targetType = "user")
    public JsonResult<Void> forceLogout(@PathVariable String userId) {
        forceLogoutService.kickOut(userId);
        return JsonResult.ok();
    }
}
