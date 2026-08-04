package com.platform.system.auth.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.audit.OpLogRecord;
import com.platform.core.infrastructure.audit.OpLogSink;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.security.ClientIpResolver;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.PasswordResetTokenEntity;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.PasswordResetTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Pre-auth password-reset endpoints, the in-house counterpart of
 * {@link InviteController}. Both legs (GET + POST) are reachable without
 * a session — the cleartext reset token is the proof of identity.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>The SSO → password migration runner mints a reset token and emails
 *       the cleartext link to the user.</li>
 *   <li>User clicks the link → frontend ResetPasswordAccept.vue
 *       → {@code GET /auth/password-reset/{token}} (probe).</li>
 *   <li>User submits a new password → {@code POST /auth/password-reset/{token}}.
 *       Server-side: validate policy, consume token, bcrypt + write
 *       {@code core_auth_user.password_hash}, NULL out {@code keycloak_id},
 *       disable the user in Keycloak.</li>
 *   <li>Frontend redirects to {@code /login} where the user signs in with
 *       their freshly-set password.</li>
 * </ol>
 *
 * <p>Why disable (not delete) the KC user: deletion loses the KC-side
 * audit trail (login history, action emails sent). Disabling makes KC
 * refuse all new tokens for that user while preserving history. If the
 * operator ever wants to roll back to OIDC, the disabled KC user can
 * be re-enabled and the JIT bind path picks it up again.
 *
 * <p>Always registered — unlike {@link InviteController}, the reset flow
 * needs to keep working AFTER the operator has switched mode back to
 * {@code password}. {@link KeycloakUserService} is wrapped in
 * {@link ObjectProvider} so this controller stays bootable when the KC
 * facade isn't on the classpath path (mode != oidc).
 */
@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

    private final PasswordResetTokenService tokens;
    private final UserMapper userMapper;
    private final PasswordEncoder encoder;
    private final PasswordPolicyService passwordPolicy;
    private final ObjectProvider<KeycloakUserService> keycloakProvider;
    private final AppMailProperties mailProps;
    /** Async best-effort audit sink — see recordAudit (this endpoint is pre-auth). */
    private final OpLogSink opLogSink;
    private final ClientIpResolver clientIpResolver;

    public PasswordResetController(PasswordResetTokenService tokens,
                                   UserMapper userMapper,
                                   PasswordEncoder encoder,
                                   PasswordPolicyService passwordPolicy,
                                   ObjectProvider<KeycloakUserService> keycloakProvider,
                                   AppMailProperties mailProps,
                                   OpLogSink opLogSink,
                                   ClientIpResolver clientIpResolver) {
        this.tokens = tokens;
        this.userMapper = userMapper;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.keycloakProvider = keycloakProvider;
        this.mailProps = mailProps;
        this.opLogSink = opLogSink;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * Probe — does this token still claim a password? Used by the
     * frontend's reset page to short-circuit the form when the link
     * is dead. Same opaque-on-failure shape as {@link InviteController#probe}.
     */
    @GetMapping("/{token}")
    public JsonResult<Map<String, Object>> probe(@PathVariable String token) {
        PasswordResetTokenEntity row = tokens.peek(token);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Reset link is no longer valid");
        }
        return JsonResult.ok(Map.of(
                "valid",     true,
                "tenantId",  row.getTenantId(),
                "expiresAt", row.getExpiresAt().toString()
        ));
    }

    /**
     * Accept the new password. After this call the user can log in via the
     * legacy password path; their previous SSO identity is permanently
     * detached. Idempotent at the token level (single-use).
     */
    @PostMapping("/{token}")
    public JsonResult<Map<String, Object>> accept(@PathVariable String token,
                                                  @Valid @RequestBody ResetPasswordRequest req,
                                                  HttpServletRequest req0) {
        passwordPolicy.validate(req.password());

        // 1. Consume FIRST so a slow / retried HTTP request can't double-spend
        //    the token across the bcrypt + KC.disableUser legs below.
        PasswordResetTokenEntity row = tokens.consume(token);

        UserEntity user = userMapper.findByIdAndTenant(row.getUserId(), row.getTenantId());
        if (user == null) {
            // Shouldn't normally happen — the token references the user by id —
            // but if it does (user was hard-deleted between mint and consume)
            // we surface a generic NOT_FOUND rather than papering over it.
            throw new BusinessException(ErrorCode.NOT_FOUND, "User no longer exists");
        }

        // 2. Stamp the new bcrypt hash AND detach from Keycloak in one UPDATE.
        //    UpdateWrapper for two reasons:
        //      - setting fields to NULL via the entity setter loses to MP's
        //        NOT_NULL field strategy (the SET column gets omitted from
        //        the UPDATE statement entirely).
        //      - keeps password_hash + keycloak_id atomic; if either alone
        //        landed the row would be in a half-migrated state.
        String newHash = encoder.encode(req.password());
        userMapper.update(null,
                new UpdateWrapper<UserEntity>()
                        .eq("id", user.getId())
                        .eq("tenant_id", user.getTenantId())
                        .set("password_hash", newHash)
                        .set("keycloak_id",   null)
                        .set("update_user",   "password-reset")
                        .set("update_time",   OffsetDateTime.now()));

        // 3. Disable the KC user so the IdP side can no longer issue
        //    tokens for this identity. Best-effort: if KC is unreachable
        //    the local password is already written and the user can log
        //    in; we just log the orphan KC user for the operator to clean
        //    up later.
        KeycloakUserService kc = keycloakProvider.getIfAvailable();
        if (kc != null && row.getKeycloakId() != null && !row.getKeycloakId().isBlank()) {
            try {
                kc.disableUser(row.getTenantId(), row.getKeycloakId());
            } catch (Exception e) {
                log.warn("[reset] could not disable orphan KC user {} in realm {} ({})",
                        row.getKeycloakId(), row.getTenantId(), e.toString());
            }
        }

        recordAudit(row.getTenantId(), user.getId(), user.getUsername(), req0);

        log.info("[reset] user {} (tenant {}) completed SSO → password reset",
                user.getId(), row.getTenantId());

        return JsonResult.ok(Map.of(
                "loginUrl", mailProps.baseUrl() + "/login"
        ));
    }

    /**
     * Audit the completed reset into {@code core_oplog}.
     *
     * <p>Why not just {@code @OpLog}: this endpoint is PRE-AUTH, so the aspect's
     * {@code RequestContext} carries no user and only whatever {@code X-Tenant-Id}
     * the caller happened to send — the row would be misattributed, which for a
     * security audit is worse than no row. So we build it explicitly from the
     * consumed token, exactly like {@code AuthService.recordBreakGlassUse}.
     *
     * <p>This was previously not audited at all: every other credential-changing
     * endpoint in the project writes an oplog row ({@code auth.breakGlassSet},
     * the two {@code reset-password} consoles, {@code auth.unlock},
     * {@code auth.forceLogout}), while the two pre-auth token endpoints that set a
     * PERMANENT password left no trace — and this one also irreversibly detaches
     * the Keycloak identity. Best-effort: the sink is {@code @Async} and swallows
     * its own errors; we guard again so audit can never fail the user's reset.
     */
    private void recordAudit(String tenantId, String userId, String username, HttpServletRequest http) {
        try {
            opLogSink.record(new OpLogRecord(
                    tenantId, userId, username,
                    "system", "auth.passwordResetAccept", "user", userId,
                    http == null ? null : http.getRequestURI(),
                    http == null ? null : http.getMethod(),
                    clientIpResolver.resolve(http),
                    http == null ? null : http.getHeader("User-Agent"),
                    null, true, null, null, 0));
        } catch (Exception e) {
            log.warn("[reset] could not record audit oplog for user {}: {}", userId, e.toString());
        }
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 128) String password) {}
}
