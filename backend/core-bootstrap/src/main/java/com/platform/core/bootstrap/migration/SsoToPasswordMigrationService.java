package com.platform.core.bootstrap.migration;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.PasswordResetTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reverse counterpart of {@link PasswordToSsoMigrationService}: emails
 * every Keycloak-bound user a reset-password link landing on our own
 * {@code /auth/password-reset/{token}} endpoint, so the operator can
 * switch the deployment from {@code mode=oidc} back to {@code mode=password}
 * without locking the user base out of the system.
 *
 * <p>Per-candidate flow:
 * <ol>
 *   <li>Skip if the row has no email (no way to deliver the link).</li>
 *   <li>{@link PasswordResetTokenService#mint} a single-use token bound
 *       to {@code (tenant, user, keycloakId)}.</li>
 *   <li>{@link MailService#sendHtmlAsync} the cleartext URL — same
 *       Freemarker pipeline as {@code user-invite} but a different
 *       template ({@code user-password-reset}) and a different landing
 *       page (the in-house reset accept, NOT the KC self-service).</li>
 * </ol>
 *
 * <p>What the controller does later (when the user clicks the link):
 * <ul>
 *   <li>passwordPolicy.validate(new password) — same complexity gate as
 *       the legacy password path.</li>
 *   <li>bcrypt the password and stamp it onto
 *       {@code core_auth_user.password_hash} via UpdateWrapper.</li>
 *   <li>NULL out {@code keycloak_id} in the same UPDATE — the row no
 *       longer claims any KC identity.</li>
 *   <li>{@code KeycloakUserService.disableUser} on the KC side so the
 *       IdP can't issue tokens for this username going forward.</li>
 * </ul>
 *
 * <p>Outcome: a user who completes the reset flow looks <em>byte-identical</em>
 * to a user born under the password regime — no {@code keycloak_id},
 * a valid bcrypt {@code password_hash}, and an audit trail of the
 * transition (token mint + consume rows + reset-flow log lines).
 *
 * <h3>Idempotency</h3>
 * The candidate query selects {@code keycloak_id IS NOT NULL}. Once a
 * user completes reset, their {@code keycloak_id} becomes NULL, so they
 * drop out of subsequent runs. A user who hasn't clicked the email yet
 * stays in the candidate set and receives a fresh token + email on
 * re-run — which is exactly the right behavior for laggard reminders.
 */
@Service
public class SsoToPasswordMigrationService {

    private static final Logger log = LoggerFactory.getLogger(SsoToPasswordMigrationService.class);

    private final UserMapper userMapper;
    private final PasswordResetTokenService tokens;
    private final MailService mailService;
    private final AppMailProperties mailProps;

    public SsoToPasswordMigrationService(UserMapper userMapper,
                                         PasswordResetTokenService tokens,
                                         MailService mailService,
                                         AppMailProperties mailProps) {
        this.userMapper = userMapper;
        this.tokens = tokens;
        this.mailService = mailService;
        this.mailProps = mailProps;
    }

    public MigrationReport run(List<String> tenantIds) {
        MigrationReport report = new MigrationReport();
        for (String tid : tenantIds) {
            if (tid == null || tid.isBlank()) continue;
            MigrationReport.TenantResult bucket = report.forTenant(tid);
            String traceId = "rev-migration-" + UUID.randomUUID().toString().replace("-", "");
            RequestContext.set(tid, "system-migration", "system-migration", Locale.JAPAN, traceId);
            try {
                migrateTenant(tid, bucket);
            } finally {
                RequestContext.clear();
            }
        }
        report.finishedAt = java.time.Instant.now();
        return report;
    }

    private void migrateTenant(String tenantId, MigrationReport.TenantResult bucket) {
        // Candidate = active row that still claims a KC identity. Once a
        // user completes the reset accept endpoint, their keycloak_id
        // becomes NULL and they fall out of this query — that's the
        // "data clean as if always password-mode" property.
        List<UserEntity> candidates = userMapper.selectList(
                new QueryWrapper<UserEntity>()
                        .eq("mark", 1)
                        .isNotNull("keycloak_id")
                        .orderByAsc("create_time"));
        log.info("[rev-migration] tenant={} found {} candidates (still KC-bound)", tenantId, candidates.size());

        for (UserEntity u : candidates) {
            if (u.getUsername() == null || u.getUsername().isBlank()) {
                bucket.skipped.add(skipped(u, "missing-username"));
                continue;
            }
            if (u.getEmail() == null || u.getEmail().isBlank()) {
                // No email = no way to deliver the reset link. Operator
                // must fill in the email column before the user can
                // transition. We skip rather than fail so the rest of
                // the batch isn't blocked.
                bucket.skipped.add(skipped(u, "missing-email"));
                continue;
            }
            String cleartextToken;
            try {
                cleartextToken = tokens.mint(tenantId, u.getId(), u.getKeycloakId());
            } catch (Exception e) {
                bucket.failed.add(failed(u, "mint-token", e));
                continue;
            }
            try {
                sendResetEmail(u, cleartextToken, tenantId);
            } catch (Exception e) {
                // The token is already minted (single-use, will expire);
                // mark as failed so the operator can re-run after fixing
                // SMTP. Re-running will mint a fresh token (the old one
                // ages out naturally on its expires_at) and try again.
                bucket.failed.add(failed(u, "send-reset-email", e));
                continue;
            }
            MigrationReport.Created c = new MigrationReport.Created();
            c.userId = u.getId();
            c.username = u.getUsername();
            c.email = u.getEmail();
            c.keycloakId = u.getKeycloakId();
            c.emailSent = true;
            bucket.created.add(c);   // bucket reused for "emails issued" tally
        }
        log.info("[rev-migration] tenant={} emails-sent={} skipped={} failed={}",
                tenantId, bucket.created.size(), bucket.skipped.size(), bucket.failed.size());
    }

    private void sendResetEmail(UserEntity u, String cleartextToken, String tenantId) {
        Map<String, Object> model = new HashMap<>();
        model.put("appName",      mailProps.fromName());
        model.put("username",     u.getUsername());
        model.put("displayName",  u.getDisplayName());
        model.put("tenantId",     tenantId);
        model.put("supportEmail", mailProps.from());
        model.put("resetUrl",     mailProps.baseUrl() + "/reset-password/" + cleartextToken);
        model.put("expiresIn",    "7");
        Object[] subjectArgs = new Object[] { "[" + mailProps.fromName() + "]" };
        // Recipient locale is hard to know at migration time (the user has
        // never logged in to set one); take JP as the conservative default,
        // matching the rest of the app's MailService fallbacks.
        // sendHtmlAsync drops the future on the floor — exceptions raise
        // back here only if it can't even SCHEDULE the send.
        mailService.sendHtmlAsync(u.getEmail(), Locale.JAPAN,
                "user-password-reset.subject", subjectArgs,
                "user-password-reset", model);
    }

    private static MigrationReport.Skipped skipped(UserEntity u, String reason) {
        MigrationReport.Skipped s = new MigrationReport.Skipped();
        s.userId = u.getId();
        s.username = u.getUsername();
        s.reason = reason;
        return s;
    }

    private static MigrationReport.Failed failed(UserEntity u, String stage, Exception e) {
        MigrationReport.Failed f = new MigrationReport.Failed();
        f.userId = u.getId();
        f.username = u.getUsername();
        f.stage = stage;
        f.errorMessage = e.toString();
        return f;
    }
}
