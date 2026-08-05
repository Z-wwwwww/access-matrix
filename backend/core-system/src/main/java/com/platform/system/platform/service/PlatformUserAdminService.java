package com.platform.system.platform.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.TempPasswords;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.auth.service.SessionTerminationService;
import com.platform.system.platform.dto.PlatformUserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manage platform-ops staff: the users that live in the {@code system} tenant
 * and hold PLATFORM_ADMIN. Gated by {@code platform:user:*}. This closes the gap
 * noted in {@code SystemAdminSeeder} (which only seeds the single dev {@code ops}
 * user) — adding further operators no longer needs hand-run SQL + Keycloak.
 *
 * <p>Cross-tenant by nature: callers are platform-ops (JWT {@code tid='system'}),
 * so the MyBatis tenant interceptor is bypassed; raw {@link JdbcTemplate} keeps
 * the writes explicitly scoped to {@code system}.
 *
 * <p><b>Create</b> mirrors the user half of {@code TenantAdminService}: provision
 * a Keycloak user in the {@code system} realm, persist the {@code core_auth_user}
 * row (no local password — KC owns the credential), and bind PLATFORM_OPERATOR.
 * The user then sets their own password via a single-use {@code /invite/{token}}
 * link (plan B — no temp password is ever handed out).
 *
 * <p>Consistency: the two DB writes live in {@link #persistNewOpsUser}, a
 * {@code @Transactional} method invoked through the Spring proxy, so they commit
 * or roll back as ONE unit; {@link #create} itself stays outside any transaction
 * so its Keycloak call isn't trapped in one. If the DB unit fails, the KC user is
 * deleted (compensation) — so a failure leaves the operator in neither system and
 * a retry can reuse the username.
 */
@Service
public class PlatformUserAdminService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUserAdminService.class);

    private static final String SYSTEM_TENANT = "system";
    private static final String SYSTEM_REALM  = "system";
    /** Numbering category for user_no — the same one every other creation path uses. */
    private static final String USER_NO_KBN = "USER";

    private final JdbcTemplate jdbc;
    /** KC is only present in oidc mode; ObjectProvider keeps this bootable otherwise. */
    private final ObjectProvider<KeycloakUserService> userServiceProvider;
    /** Used to terminate a disabled/deleted operator's in-flight sessions immediately. */
    private final ForceLogoutService forceLogoutService;
    /** App SMTP (CORE_MAIL_*); absent if mail isn't configured. Onboarding emails go
        through this (the app's own SMTP), NOT the per-realm Keycloak SMTP. */
    private final ObjectProvider<MailService> mailProvider;
    private final AppMailProperties mailProps;
    /** Single-use invite-token mint — backs the "resend invite link" (plan B). */
    private final InviteTokenService inviteTokenService;
    /** Shared owner of enable/disable session + Keycloak side-effects (see setEnabled). */
    private final SessionTerminationService sessionTermination;
    /** Per-tenant user_no allocation — see {@link #nextSystemUserNo()}. */
    private final NumberingService numberingService;
    /**
     * Self-reference (through the Spring proxy) used to invoke the
     * {@code @Transactional} {@link #persistNewOpsUser} from the NON-transactional
     * {@link #create}. A plain {@code this.persistNewOpsUser(...)} self-call would
     * bypass the proxy and the advice, leaving the two INSERTs as independent
     * autocommits. Same pattern (and same reason) as
     * {@code TenantAdminService.persistNewTenant}: create() itself must stay
     * outside any transaction so its Keycloak call isn't trapped in one.
     */
    private final ObjectProvider<PlatformUserAdminService> self;

    public PlatformUserAdminService(JdbcTemplate jdbc,
                                    ObjectProvider<KeycloakUserService> userServiceProvider,
                                    ForceLogoutService forceLogoutService,
                                    ObjectProvider<MailService> mailProvider,
                                    AppMailProperties mailProps,
                                    InviteTokenService inviteTokenService,
                                    SessionTerminationService sessionTermination,
                                    NumberingService numberingService,
                                    ObjectProvider<PlatformUserAdminService> self) {
        this.jdbc = jdbc;
        this.userServiceProvider = userServiceProvider;
        this.forceLogoutService = forceLogoutService;
        this.mailProvider = mailProvider;
        this.mailProps = mailProps;
        this.inviteTokenService = inviteTokenService;
        this.sessionTermination = sessionTermination;
        this.numberingService = numberingService;
        this.self = self;
    }

    /**
     * Upper bound on {@code size}, mirroring
     * {@code MybatisPlusConfig}'s {@code PaginationInnerInterceptor.setMaxLimit(500)}.
     * This is the ONLY hand-rolled paginator in the codebase — every other list goes
     * through MyBatis-Plus, which normalises {@code page < 1} and caps {@code size}
     * for free. Without the same clamping here the endpoint diverged from all the
     * others in two ways, both reachable from an unvalidated query param
     * (the controller only declares {@code @RequestParam(defaultValue = ...)}):
     * <ul>
     *   <li>{@code ?page=0} → {@code offset = (0-1)*20 = -20} → Postgres rejects a
     *       negative OFFSET → 500 "Unhandled exception" (with the stack detail
     *       exposed in dev). {@code ?size=-5} → negative LIMIT, same outcome.</li>
     *   <li>{@code ?size=1000000} → no cap at all, unlike every MyBatis list.</li>
     * </ul>
     */
    private static final long MAX_PAGE_SIZE = 500;

    public PageResult<PlatformUserDto.View> list(long page, long size, String keyword) {
        page = Math.max(1, page);
        size = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        StringBuilder where = new StringBuilder(
                "FROM core_auth_user u WHERE u.tenant_id = ? AND u.mark = 1");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.username ILIKE ? OR u.email ILIKE ? OR u.display_name ILIKE ?)");
        }
        Object[] countArgs = keyword == null || keyword.isBlank()
                ? new Object[] { SYSTEM_TENANT }
                : new Object[] { SYSTEM_TENANT, like(keyword), like(keyword), like(keyword) };
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + where, Long.class, countArgs);
        long totalVal = total == null ? 0 : total;

        long offset = (page - 1) * size;
        Object[] pageArgs = new Object[countArgs.length + 2];
        System.arraycopy(countArgs, 0, pageArgs, 0, countArgs.length);
        pageArgs[countArgs.length] = size;
        pageArgs[countArgs.length + 1] = offset;

        List<PlatformUserDto.View> rows = jdbc.query(
                "SELECT u.id, u.username, u.email, u.display_name, u.status, u.create_time, "
                        + "       EXISTS(SELECT 1 FROM core_rbac_user_role r "
                        + "              WHERE r.user_id = u.id AND r.tenant_id = '" + SYSTEM_TENANT + "' "
                        + "                AND r.role_id = '" + BuiltInRoles.PLATFORM_ADMIN_ID + "' AND r.mark = 1) AS platform_admin "
                        + where + " ORDER BY u.create_time DESC LIMIT ? OFFSET ?",
                (rs, n) -> new PlatformUserDto.View(
                        rs.getString("id"), rs.getString("username"), rs.getString("email"),
                        rs.getString("display_name"), (Integer) rs.getObject("status"),
                        rs.getBoolean("platform_admin"),
                        rs.getObject("create_time", java.time.OffsetDateTime.class)),
                pageArgs);
        return PageResult.of(rows, totalVal, page, size);
    }

    public PlatformUserDto.CreateResponse create(PlatformUserDto.CreateRequest req) {
        // Precise duplicate checks BEFORE touching Keycloak — otherwise an email
        // clash surfaces as KC's generic CONFLICT (message mentions the username)
        // and misleads the operator into thinking the username is taken.
        Long usernameDup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core_auth_user WHERE tenant_id = ? AND username = ? AND mark = 1",
                Long.class, SYSTEM_TENANT, req.username());
        if (usernameDup != null && usernameDup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.opsuser.usernameExists");
        }
        Long emailDup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core_auth_user WHERE tenant_id = ? AND email = ? AND mark = 1",
                Long.class, SYSTEM_TENANT, req.email());
        if (emailDup != null && emailDup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.opsuser.emailExists");
        }
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Keycloak is not enabled — platform user provisioning requires app.security.mode=oidc");
        }

        // No temp password — the user sets their own via the invite link (plan B,
        // the SAME path as resend). External mutation first; compensated below if
        // the DB half fails.
        String kcId = kc.createUser(SYSTEM_REALM, req.username(), req.email(), req.displayName(), null);
        try {
            // Both DB writes as ONE unit, through the proxy so @Transactional
            // applies. Previously they were two independent autocommits: if the
            // user_role INSERT failed, the core_auth_user row stayed committed
            // while the catch below deleted the KC user — leaving a roleless row
            // whose keycloak_id pointed at a deleted KC user, and whose username
            // then tripped the usernameDup pre-check on every retry.
            String userId = self.getObject().persistNewOpsUser(req, kcId);

            // Send the invite link (plan B) — the SAME path as resend: mint a
            // single-use /invite/{token} and email it; the user sets their own
            // password on the landing page. No temp password is set or returned.
            // Best-effort: a mail failure must NOT fail creation (operator can use
            // "resend" to retry). Deliberately AFTER the transaction commits so the
            // invite row can never reference an uncommitted user.
            boolean emailSent = sendInviteMail(userId, kcId, req.username(), req.email(), req.displayName());
            log.info("[platform-user] provisioned ops user '{}' (id={}, kcId={}) with PLATFORM_OPERATOR, inviteSent={}",
                    req.username(), userId, kcId, emailSent);
            return new PlatformUserDto.CreateResponse(userId, req.username(), emailSent);
        } catch (RuntimeException e) {
            // Compensation: remove the orphan KC user so a retry can reuse the username.
            try {
                kc.deleteUser(SYSTEM_REALM, kcId);
                log.warn("[platform-user] create failed for '{}' — compensated by deleting the orphan KC user",
                        req.username(), e);
            } catch (RuntimeException ce) {
                log.error("[platform-user] create failed for '{}' AND KC compensation failed — "
                        + "orphan Keycloak user {} needs manual cleanup", req.username(), kcId, ce);
            }
            throw e;
        }
    }

    /**
     * Pure-DB half of {@link #create}: the {@code core_auth_user} row plus its
     * PLATFORM_OPERATOR binding, committed or rolled back as one unit. Contains
     * NO external (Keycloak / SMTP) calls — the invite email is sent by the
     * caller after this commits.
     *
     * @return the new user's id (ULID)
     */
    @Transactional
    public String persistNewOpsUser(PlatformUserDto.CreateRequest req, String kcId) {
        String userId = IdGenerator.ulid();
        String userNo = nextSystemUserNo();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
                "INSERT INTO core_auth_user "
                        + "  (id, tenant_id, username, email, user_no, display_name, "
                        + "   password_hash, keycloak_id, status, mark, "
                        + "   create_user, update_user, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, 1, 1, "
                        + "        'platform-admin', 'platform-admin', ?, ?)",
                userId, SYSTEM_TENANT, req.username(), req.email(), userNo,
                req.displayName(), kcId, now, now);
        jdbc.update(
                "INSERT INTO core_rbac_user_role "
                        + "  (id, tenant_id, user_id, role_id, mark, create_user, update_user) "
                        + "VALUES (?, ?, ?, ?, 1, 'platform-admin', 'platform-admin')",
                IdGenerator.ulid(), SYSTEM_TENANT, userId, BuiltInRoles.PLATFORM_OPERATOR_ID);
        return userId;
    }

    /** Enable or disable a platform operator. KC first (stronger gate), then DB status. */
    @Transactional
    public void setEnabled(String id, boolean enabled) {
        Target t = requireManageable(id);
        jdbc.update("UPDATE core_auth_user SET status = ?, update_time = ? WHERE id = ?",
                enabled ? 1 : 0, OffsetDateTime.now(), t.id());
        // Session + Keycloak side-effects (KC user enable/disable, kick/clear, end
        // session on disable) are owned by SessionTerminationService — the SAME
        // path the business-user console uses, so the two can't drift apart.
        sessionTermination.applyEnabled(t.id(), enabled);
        log.info("[platform-user] {} ops user '{}' (id={})", enabled ? "enabled" : "disabled", t.username(), t.id());
    }

    /** Soft-delete a platform operator (DB first so app access stops; KC user removed best-effort). */
    @Transactional
    public void delete(String id) {
        Target t = requireManageable(id);
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("UPDATE core_rbac_user_role SET mark = 0, update_time = ? "
                + "WHERE user_id = ? AND tenant_id = ? AND mark = 1", now, t.id(), SYSTEM_TENANT);
        jdbc.update("UPDATE core_auth_user SET mark = 0, update_time = ? WHERE id = ?", now, t.id());
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc != null && t.keycloakId() != null && !t.keycloakId().isBlank()) {
            try {
                kc.deleteUser(SYSTEM_REALM, t.keycloakId());
            } catch (RuntimeException e) {
                // DB row is already gone; a lingering KC user would only re-provision
                // a roleless account on next login. Log for manual cleanup.
                log.warn("[platform-user] deleted DB user '{}' but KC delete failed (kcId={}): {}",
                        t.username(), t.keycloakId(), e.toString());
            }
        }
        forceLogoutService.kickOut(t.id());   // terminate any in-flight session immediately
        log.info("[platform-user] deleted ops user '{}' (id={})", t.username(), t.id());
    }

    /** Reset password — re-issue credentials with the password-reset email wording. */
    public PlatformUserDto.ResetPwResponse resetPassword(String id) {
        return reissueCredentials(id, /* reset = */ true);
    }

    /**
     * Force-logout: invalidate the user's active sessions (already-issued tokens are
     * rejected on their next request). The account stays enabled — the user can sign
     * in again immediately. For when a session may be compromised, without disabling
     * the account.
     */
    public void forceLogout(String id) {
        Target t = requireManageable(id);
        forceLogoutService.kickOut(t.id());
        // Also end the KC SSO session — otherwise an SSO redirect silently
        // re-authenticates the user (KC session still valid → fresh token) and the
        // "force logout" has no visible effect.
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc != null && t.keycloakId() != null && !t.keycloakId().isBlank()) {
            kc.logoutUser(SYSTEM_REALM, t.keycloakId());
        }
        log.info("[platform-user] force-logged-out ops user '{}' (id={})", t.username(), t.id());
    }

    /**
     * Resend the onboarding INVITE (plan B): email a single-use {@code /invite/{token}}
     * link so the user sets their OWN permanent password on the landing page. Unlike
     * reset, this does NOT rotate/expose a temp password — the current password is
     * untouched until the user completes the link. Any still-open invite is
     * invalidated first so only the newest link works.
     */
    public void resendInvite(String id) {
        Target t = requireManageable(id);
        if (t.email() == null || t.email().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "ユーザーにメールアドレスが設定されていません。先に編集でメールを設定してください。");
        }
        if (t.keycloakId() == null || t.keycloakId().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Keycloak ユーザーが見つかりません。");
        }
        if (!sendInviteMail(t.id(), t.keycloakId(), t.username(), t.email(), t.displayName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "メール送信に失敗しました。CORE_MAIL_* の設定をご確認ください。");
        }
        log.info("[platform-user] resent invite link to ops user '{}' (id={})", t.username(), t.id());
    }

    /**
     * Email a single-use invite link (/invite/{token}) via the app MailService so the
     * user sets their own permanent password on the landing page (plan B). Invalidates
     * any still-open invite for this user first (only the newest link works). Reuses
     * the shared {@code user-invite} template + {@code InviteController} flow
     * ({@code tenantId="system"} → system realm). Returns true on dispatch.
     */
    private boolean sendInviteMail(String userId, String kcId, String username, String email, String displayName) {
        MailService mail = mailProvider.getIfAvailable();
        if (mail == null || email == null || email.isBlank()) {
            log.warn("[platform-user] skipped invite email for '{}' — mail service or email unavailable", username);
            return false;
        }
        try {
            OffsetDateTime now = OffsetDateTime.now();
            jdbc.update("UPDATE core_user_invite SET used_at = ?, update_time = ? "
                            + "WHERE user_id = ? AND tenant_id = ? AND used_at IS NULL",
                    now, now, userId, SYSTEM_TENANT);
            String token = inviteTokenService.mint(SYSTEM_TENANT, userId, kcId);
            Map<String, Object> model = new HashMap<>();
            model.put("appName", mailProps.fromName());
            model.put("username", username);
            model.put("displayName", displayName);
            model.put("tenantId", SYSTEM_TENANT);
            model.put("supportEmail", mailProps.from());
            model.put("inviteUrl", mailProps.baseUrl() + "/invite/" + token);
            model.put("expiresIn", String.valueOf(inviteTokenService.ttlDays()));
            Object[] subjectArgs = new Object[] { "[" + mailProps.fromName() + "]" };
            Locale locale = RequestContext.locale();
            if (locale == null) locale = Locale.JAPAN;
            mail.sendHtmlAsync(email, locale, "user-invite.subject", subjectArgs, "user-invite", model);
            return true;
        } catch (RuntimeException e) {
            log.warn("[platform-user] invite email dispatch failed for '{}': {}", username, e.toString());
            return false;
        }
    }

    /**
     * Single re-issue path behind BOTH "reset password" and "resend email": ALWAYS
     * rotates to a fresh single-use temporary password (so the user's current
     * password becomes invalid — the UI must confirm this) and best-effort emails
     * the credentials (login URL + username + temp password) via the app's
     * MailService. {@code reset} only switches the email wording / subject:
     * true → "password reset", false → "account opened / welcome".
     */
    private PlatformUserDto.ResetPwResponse reissueCredentials(String id, boolean reset) {
        Target t = requireManageable(id);
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Keycloak is not enabled — requires app.security.mode=oidc");
        }
        if (t.keycloakId() == null || t.keycloakId().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "User has no Keycloak link");
        }
        String tempPassword = TempPasswords.generate();
        kc.setPassword(SYSTEM_REALM, t.keycloakId(), tempPassword, true);
        // The new password invalidates existing sessions — force-logout so already
        // issued tokens are rejected (kickOut), AND end the KC SSO session (logoutUser)
        // so an SSO redirect can't silently re-authenticate with the old session.
        // The user CAN sign in again with the new credentials.
        forceLogoutService.kickOut(t.id());
        kc.logoutUser(SYSTEM_REALM, t.keycloakId());
        boolean emailSent = sendCredentialsMail(t.username(), t.email(), t.displayName(), tempPassword, reset);
        log.info("[platform-user] re-issued credentials for ops user '{}' (id={}), reset={}, emailSent={}",
                t.username(), t.id(), reset, emailSent);
        return new PlatformUserDto.ResetPwResponse(t.username(), tempPassword, emailSent);
    }

    /**
     * Correct a platform operator's email + display name (username is immutable).
     * Syncs Keycloak (email kept verified, first/last re-derived) and the local
     * {@code core_auth_user} row so the list/search and KC stay consistent.
     */
    @Transactional
    public void update(String id, PlatformUserDto.UpdateRequest req) {
        Target t = requireManageable(id);
        // Same precise duplicate pre-check as create, excluding the row being
        // edited — email doubles as a login identifier, and KC's own
        // duplicate rejection only surfaces as a generic operationFailed.
        if (req.email() != null && !req.email().isBlank()) {
            Long emailDup = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM core_auth_user WHERE tenant_id = ? AND email = ? AND mark = 1 AND id <> ?",
                    Long.class, SYSTEM_TENANT, req.email(), t.id());
            if (emailDup != null && emailDup > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.opsuser.emailExists");
            }
        }
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc != null && t.keycloakId() != null && !t.keycloakId().isBlank()) {
            kc.updateProfile(SYSTEM_REALM, t.keycloakId(), req.email(), req.displayName());
        }
        jdbc.update("UPDATE core_auth_user SET email = ?, display_name = ?, update_time = ? WHERE id = ?",
                req.email(), req.displayName(), OffsetDateTime.now(), t.id());
        log.info("[platform-user] updated ops user '{}' (id={}) email/displayName", t.username(), t.id());
    }

    /**
     * Resend the account-setup email: triggers Keycloak's UPDATE_PASSWORD
     * required-action email so the user sets their own password via the OIDC
     * reset-credentials page (no temp-password hand-off needed). Requires the
     * system realm's SMTP to be configured — KC returns 500 otherwise.
     */
    /**
     * Email the account-opening "welcome" message via the app's {@link MailService}
     * (CORE_MAIL_*, NOT Keycloak's per-realm SMTP). It carries the login URL +
     * username + temporary password; the user logs in and Keycloak forces a password
     * change on first login (the temp credential is single-use, so it can't be reused
     * to reset the password afterwards). Returns true on dispatch; swallows failures
     * so the caller decides whether that's fatal. Reuses the shared
     * {@code user-direct-welcome} template (same as system-user direct provisioning).
     */
    private boolean sendCredentialsMail(String username, String email, String displayName,
                                        String tempPassword, boolean reset) {
        MailService mail = mailProvider.getIfAvailable();
        if (mail == null || email == null || email.isBlank()) {
            log.warn("[platform-user] skipped credentials email for '{}' — mail service or email unavailable", username);
            return false;
        }
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("appName", mailProps.fromName());
            model.put("username", username);
            model.put("displayName", displayName);
            model.put("tenantId", SYSTEM_TENANT);
            model.put("supportEmail", mailProps.from());
            model.put("loginUrl", mailProps.baseUrl() + "/login");
            model.put("tempPassword", tempPassword);
            model.put("reset", reset);   // template switches headline + body wording on this flag
            String subjectKey = reset ? "user-account-reset.subject" : "user-direct-welcome.subject";
            Object[] subjectArgs = new Object[] { "[" + mailProps.fromName() + "]" };
            Locale locale = RequestContext.locale();
            if (locale == null) locale = Locale.JAPAN;
            mail.sendHtmlAsync(email, locale, subjectKey, subjectArgs, "user-direct-welcome", model);
            return true;
        } catch (RuntimeException e) {
            log.warn("[platform-user] credentials email dispatch failed for '{}': {}", username, e.toString());
            return false;
        }
    }

    /**
     * Load a manageable target: must be a system-tenant user, NOT the caller
     * themselves, and NOT a PLATFORM_ADMIN (so the super 'ops' account can never
     * be disabled/deleted/reset through this console).
     */
    private Target requireManageable(String id) {
        Map<String, Object> u;
        try {
            u = jdbc.queryForMap(
                    "SELECT id, username, email, display_name, keycloak_id FROM core_auth_user "
                            + "WHERE id = ? AND tenant_id = ? AND mark = 1", id, SYSTEM_TENANT);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Platform user not found: " + id);
        }
        String userId = (String) u.get("id");
        if (userId.equals(RequestContext.userId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "You cannot manage your own account here");
        }
        Long isSuper = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core_rbac_user_role WHERE user_id = ? AND tenant_id = ? "
                        + "AND role_id = ? AND mark = 1",
                Long.class, userId, SYSTEM_TENANT, BuiltInRoles.PLATFORM_ADMIN_ID);
        if (isSuper != null && isSuper > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Cannot manage a platform admin (ops) account");
        }
        return new Target(userId, (String) u.get("username"), (String) u.get("email"),
                (String) u.get("display_name"), (String) u.get("keycloak_id"));
    }

    private record Target(String id, String username, String email, String displayName, String keycloakId) {}

    /**
     * Next {@code user_no} for the system tenant — through the shared
     * {@link NumberingService}, exactly like every other creation path
     * ({@code UserAdminService.create}, {@code TenantAdminService.persistNewTenant},
     * {@code OidcJitUserService}).
     *
     * <p>This used to hand-roll {@code MAX(CAST(SUBSTRING(user_no FROM 2)))+1},
     * which advanced nothing: the {@code core_numbering_management} counter for
     * {@code system} never learned about the numbers this console handed out. The
     * two allocators then drifted apart and eventually hand out the SAME value —
     * verified on the live dev DB, where the counter's next value {@code U00000002}
     * is already held by an operator this console created:
     *
     * <pre>
     *  tenant_id | counter_now | next_from_counter | max_in_use | next_already_taken
     *  system    |           1 | U00000002         | U00000002  | t
     * </pre>
     *
     * <p>{@code (tenant_id, user_no)} is uniquely indexed
     * ({@code uk_core_auth_user_tenant_user_no}, partial on {@code mark = 1 AND
     * user_no IS NOT NULL}), so the colliding allocation is a hard insert failure.
     * The victim is whichever path allocates NEXT from the counter — most
     * plausibly {@code OidcJitUserService}, when an operator created straight in the
     * Keycloak {@code system} realm first signs in: that class guards the numbering
     * call itself with try/catch but NOT the {@code insert}, so the duplicate key
     * escapes as a 500 on every request that user makes.
     *
     * <p>The hand-rolled version was racy on its own terms too — two concurrent
     * creates read the same MAX and format the same number, while
     * {@code NumberingService} increments atomically in one
     * {@code UPDATE ... RETURNING}.
     *
     * <p>V59 re-syncs the counters that already drifted; without it the first
     * allocation after this change would still land on a taken number.
     */
    private String nextSystemUserNo() {
        return numberingService.next(USER_NO_KBN, SYSTEM_TENANT);
    }

    private static String like(String kw) {
        return "%" + kw + "%";
    }
}
