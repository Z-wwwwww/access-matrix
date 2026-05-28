package com.platform.system.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.core.infrastructure.security.keycloak.KeycloakRealmService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.platform.dto.TenantDto;
import com.platform.system.platform.entity.TenantEntity;
import com.platform.system.platform.mapper.TenantMapper;
import com.platform.system.rbac.service.RbacSeederService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tenant CRUD for platform-ops callers. All operations are bound to the
 * platform-ops authority surface ({@code platform:tenant:*}) — see
 * {@code PlatformTenantController} for the controller-layer gating.
 *
 * <h3>Two-sided writes</h3>
 * Creating, suspending, or soft-deleting a tenant changes state in TWO places:
 *
 * <ul>
 *   <li><b>Keycloak</b> — the realm itself. {@link KeycloakRealmService}
 *       handles realm creation / enable / disable.</li>
 *   <li><b>core_tenant</b> — our central registry row.</li>
 * </ul>
 *
 * <p>We always touch Keycloak FIRST for destructive operations. If
 * Keycloak fails we never touch the DB → no orphan row. If Keycloak
 * succeeds and the DB step fails, the realm is the leftover (the
 * operator can either retry to recover or manually delete the realm).
 *
 * <h3>Onboarding flow on create</h3>
 * Inside the same transaction, {@link #create} also:
 * <ol>
 *   <li>Seeds the new tenant's numbering counters (so user_no allocation works).</li>
 *   <li>Seeds the new tenant's RBAC scaffolding (SUPER_ADMIN role +
 *       {@code tenant:*} permission + cloned menus) via
 *       {@link RbacSeederService}.</li>
 *   <li>Creates the first admin user (no password) and binds them to
 *       SUPER_ADMIN.</li>
 *   <li>Creates the matching Keycloak user (no credentials) in the new
 *       realm so the OIDC JIT bind path can link them on first login.</li>
 *   <li>Mints an invite token and emails it to {@code contactEmail} so
 *       the recipient sets their own password via the invite landing page.</li>
 * </ol>
 *
 * <p>The mail step is wrapped in a try/catch — a flaky SMTP must not
 * roll back the tenant. If the email never arrives, the operator can
 * trigger a resend later (TODO: separate "resend invite" endpoint).
 *
 * <h3>Soft delete vs suspend</h3>
 * <ul>
 *   <li><b>Suspend</b> (status=0, mark=1) — temporary pause. Realm is
 *       disabled in KC so logins fail, but the tenant stays visible in
 *       the platform list and is easily resumable.</li>
 *   <li><b>Soft delete</b> (mark=0) — tenant removed from the list. Same
 *       KC effect, but recovery requires DB edit or a future "restore"
 *       feature. Business data stays untouched.</li>
 *   <li>Hard delete is intentionally NOT exposed — see follow-up task.</li>
 * </ul>
 */
@Service
public class TenantAdminService {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminService.class);

    /** tenant codes reserved by the project — never available to customers. */
    private static final Set<String> RESERVED_CODES = Set.of("system", "demo");

    /** Lowercase alphanumeric + dash/underscore, 1..64 chars. Username constraint. */
    private static final Pattern USERNAME_OK = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    /** Used by NumberingService.next when allocating the new admin's user_no. */
    private static final String USER_NO_KBN = "USER";

    private final TenantMapper tenantMapper;
    /**
     * Keycloak realm operations are only available when
     * {@code app.security.mode=oidc}; in other modes {@link KeycloakRealmService}
     * isn't a bean. ObjectProvider keeps this service bootable in those
     * modes (where tenant management is meaningless anyway and the
     * controller is gated separately).
     */
    private final ObjectProvider<KeycloakRealmService> realmServiceProvider;
    private final ObjectProvider<KeycloakUserService> userServiceProvider;
    private final NumberingService numberingService;
    private final RbacSeederService rbacSeederService;
    private final InviteTokenService inviteTokenService;
    private final ObjectProvider<MailService> mailProvider;
    private final AppMailProperties mailProps;
    private final JdbcTemplate jdbc;

    public TenantAdminService(TenantMapper tenantMapper,
                              ObjectProvider<KeycloakRealmService> realmServiceProvider,
                              ObjectProvider<KeycloakUserService> userServiceProvider,
                              NumberingService numberingService,
                              RbacSeederService rbacSeederService,
                              InviteTokenService inviteTokenService,
                              ObjectProvider<MailService> mailProvider,
                              AppMailProperties mailProps,
                              JdbcTemplate jdbc) {
        this.tenantMapper = tenantMapper;
        this.realmServiceProvider = realmServiceProvider;
        this.userServiceProvider = userServiceProvider;
        this.numberingService = numberingService;
        this.rbacSeederService = rbacSeederService;
        this.inviteTokenService = inviteTokenService;
        this.mailProvider = mailProvider;
        this.mailProps = mailProps;
        this.jdbc = jdbc;
    }

    public PageResult<TenantDto.View> list(long page, long size, String keyword) {
        Page<TenantEntity> p = new Page<>(page, size);
        QueryWrapper<TenantEntity> w = new QueryWrapper<TenantEntity>()
                .eq("mark", 1)
                .orderByDesc("create_time");
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("tenant_code", keyword).or().like("display_name", keyword));
        }
        Page<TenantEntity> result = tenantMapper.selectPage(p, w);
        List<TenantDto.View> records = result.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public TenantDto.View get(String id) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }
        return toView(row);
    }

    @Transactional
    public String create(TenantDto.CreateRequest req) {
        // ── 1. Validate ─────────────────────────────────────────────
        if (RESERVED_CODES.contains(req.tenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tenant code '" + req.tenantCode() + "' is reserved");
        }
        if (tenantMapper.findActiveByCode(req.tenantCode()) != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tenant code '" + req.tenantCode() + "' already exists");
        }
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Keycloak is not enabled — tenant provisioning requires app.security.mode=oidc");
        }
        if (realmService.realmExists(req.tenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Realm '" + req.tenantCode() + "' already exists in Keycloak — "
                            + "either pick a different code or import it via the DB after manual cleanup");
        }

        // ── 2. Create realm in Keycloak ─────────────────────────────
        // If this throws, we haven't touched the DB yet → no orphan row.
        realmService.createRealmFromTemplate(req.tenantCode(), req.displayName());

        // ── 3. Insert registry row ──────────────────────────────────
        // If THIS fails, the realm is orphaned in Keycloak. Retry of the
        // same request will fail at the realmExists check above; the
        // operator must either manually delete the orphan realm or
        // hand-edit core_tenant to add the row.
        TenantEntity row = new TenantEntity();
        row.setId(IdGenerator.ulid());
        row.setTenantId("system");
        row.setTenantCode(req.tenantCode());
        row.setDisplayName(req.displayName());
        row.setContactEmail(req.contactEmail());
        row.setStatus(1);
        row.setMark(1);
        row.setCreateUser("platform-admin");
        row.setUpdateUser("platform-admin");
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        tenantMapper.insert(row);

        // ── 4. Seed per-tenant numbering definitions ────────────────
        // Without this, the new tenant's first numberingService.next("USER", ...)
        // would error with "Numbering definition not found".
        numberingService.seedDefaultsForTenant(req.tenantCode());

        // ── 5. Seed RBAC scaffolding (role + perm + menus) ──────────
        // Returns the new SUPER_ADMIN role id so we can bind the admin user.
        String superAdminRoleId = rbacSeederService.seedDefaultsForTenant(req.tenantCode());

        // ── 6. Provision the first admin user (no password, invite-driven) ──
        String adminUsername = resolveAdminUsername(req);
        String adminEmail = req.contactEmail();
        String adminDisplayName = req.displayName() + " Admin";

        // 6a. Keycloak user — no credentials. The invite landing page will
        // call setPassword after the recipient picks one. Failure here aborts
        // the whole transaction (realm is left as the orphan, same as step 3).
        String kcId = null;
        KeycloakUserService userService = userServiceProvider.getIfAvailable();
        if (userService != null) {
            kcId = userService.createUser(req.tenantCode(), adminUsername, adminEmail,
                    adminDisplayName, /* tempPassword = */ null);
        }

        // 6b. Business user row. Use JdbcTemplate so we set tenant_id
        // explicitly to the NEW tenant — going through UserMapper would
        // pick up RequestContext.tenantId() = 'system' via AuditMetaObjectHandler.
        String userId = IdGenerator.ulid();
        String userNo = numberingService.next(USER_NO_KBN, req.tenantCode());
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO core_auth_user "
                        + "  (id, tenant_id, username, email, user_no, display_name, "
                        + "   password_hash, keycloak_id, status, mark, "
                        + "   create_user, update_user, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, 1, 1, "
                        + "        'platform-admin', 'platform-admin', ?, ?)",
                userId, req.tenantCode(), adminUsername, adminEmail, userNo,
                adminDisplayName, kcId, now, now);

        // 6c. user_role binding to SUPER_ADMIN.
        jdbc.update(
                "INSERT INTO core_rbac_user_role "
                        + "  (id, tenant_id, user_id, role_id, mark, create_user, update_user) "
                        + "VALUES (?, ?, ?, ?, 1, 'platform-admin', 'platform-admin')",
                IdGenerator.ulid(), req.tenantCode(), userId, superAdminRoleId);

        // ── 7. Mint invite + send email ─────────────────────────────
        // Mint stays inside the transaction (the token row is durable).
        // sendHtmlAsync is fire-and-forget; if mail fails, the row exists
        // and operator can re-mint via a follow-up "resend" feature.
        String token = inviteTokenService.mint(req.tenantCode(), userId, kcId);
        sendInviteMail(adminUsername, adminEmail, adminDisplayName,
                req.tenantCode(), token);

        log.info("[tenant] created tenant '{}' (id={}, displayName='{}') with admin '{}' invited",
                req.tenantCode(), row.getId(), req.displayName(), adminUsername);
        return row.getId();
    }

    /**
     * Resolve the admin username from the create request. If the operator
     * provided one, validate and use it. Otherwise derive from
     * {@code contactEmail}'s local-part: lowercase, keep alphanumeric +
     * dash/underscore, drop everything else (so {@code info+team@acme.com}
     * → {@code infoteam}); fall back to {@code "admin"} on empty result.
     */
    static String deriveUsernameFromEmail(String email) {
        if (email == null) return "admin";
        int at = email.indexOf('@');
        // `at >= 0` not `> 0` so the pathological "@acme.com" (empty local-part)
        // takes the local="" path and falls back to "admin", instead of slurping
        // the domain and yielding something like "acmecom".
        String local = at >= 0 ? email.substring(0, at) : email;
        // Trim to charset; collapse anything non-matching to nothing.
        StringBuilder sb = new StringBuilder(local.length());
        for (char c : local.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                sb.append(c);
            }
        }
        String derived = sb.toString();
        if (derived.isEmpty()) return "admin";
        // Username MUST start with alphanumeric per USERNAME_OK regex; strip
        // leading separators by walking forward to the first ok char.
        int start = 0;
        while (start < derived.length() && (derived.charAt(start) == '-' || derived.charAt(start) == '_')) {
            start++;
        }
        if (start == derived.length()) return "admin";
        derived = derived.substring(start);
        if (derived.length() > 64) derived = derived.substring(0, 64);
        return derived;
    }

    private String resolveAdminUsername(TenantDto.CreateRequest req) {
        if (req.adminUsername() != null && !req.adminUsername().isBlank()) {
            if (!USERNAME_OK.matcher(req.adminUsername()).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "adminUsername must match [a-z0-9][a-z0-9_-]{0,63}");
            }
            return req.adminUsername();
        }
        return deriveUsernameFromEmail(req.contactEmail());
    }

    private void sendInviteMail(String username, String email, String displayName,
                                String tenantCode, String token) {
        MailService mail = mailProvider.getIfAvailable();
        if (mail == null || email == null || email.isBlank()) {
            log.warn("[tenant] skipped invite email for tenant '{}' admin '{}' — mail service or email unavailable",
                    tenantCode, username);
            return;
        }
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("appName", mailProps.fromName());
            model.put("username", username);
            model.put("displayName", displayName);
            model.put("tenantId", tenantCode);
            model.put("supportEmail", mailProps.from());
            model.put("inviteUrl", mailProps.baseUrl() + "/invite/" + token);
            model.put("expiresIn", "7");

            Object[] subjectArgs = new Object[] { "[" + mailProps.fromName() + "]" };
            // Recipient locale: the platform admin's current locale is the best
            // guess. The new tenant admin has no profile to ask yet; once they
            // accept the invite and set a locale, subsequent emails will follow it.
            Locale locale = RequestContext.locale();
            if (locale == null) locale = Locale.JAPAN;

            mail.sendHtmlAsync(email, locale,
                    "user-invite.subject", subjectArgs,
                    "user-invite", model);
        } catch (Exception e) {
            log.warn("[tenant] invite email dispatch failed for tenant '{}' admin '{}': {}",
                    tenantCode, username, e.toString());
            // Don't propagate — registry row is already committed, retry via
            // a separate resend flow (TODO).
        }
    }

    /**
     * Update mutable registry fields. {@code tenant_code} stays immutable —
     * renaming it would require coordinated changes across the Keycloak realm,
     * every business row's tenant_id, and any external client linking. Use
     * hard-delete + recreate if a rename is truly needed.
     *
     * <p>Also patches Keycloak's realm displayName attribute so the admin
     * console matches what the platform console shows.
     */
    @Transactional
    public void update(String id, TenantDto.UpdateRequest req) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }

        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.updateDisplayName(row.getTenantCode(), req.displayName());
            } catch (Exception e) {
                log.warn("[tenant] KC updateDisplayName for '{}' failed: {}",
                        row.getTenantCode(), e.toString());
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not update realm displayName in Keycloak: " + e.getMessage());
            }
        }

        tenantMapper.update(null,
                new UpdateWrapper<TenantEntity>()
                        .eq("id", id)
                        .eq("mark", 1)
                        .set("display_name", req.displayName())
                        .set("contact_email", req.contactEmail())
                        .set("update_user", "platform-admin")
                        .set("update_time", LocalDateTime.now()));

        log.info("[tenant] updated tenant '{}' (id={}) — displayName='{}', contactEmail='{}'",
                row.getTenantCode(), id, req.displayName(), req.contactEmail());
    }

    /**
     * Suspend a tenant: status=0 (visible in list, marked paused) and the
     * Keycloak realm disabled so logins fail. Reversible from the UI via
     * {@link #resume}; symmetric with soft-delete on the KC side but keeps
     * the registry row mark=1.
     */
    @Transactional
    public void suspend(String id) {
        TenantEntity row = requireActiveTenantNotBuiltIn(id, "suspend");
        if (Integer.valueOf(0).equals(row.getStatus())) {
            return; // already suspended — idempotent
        }
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.disableRealm(row.getTenantCode());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not disable realm in Keycloak: " + e.getMessage());
            }
        }
        tenantMapper.update(null,
                new UpdateWrapper<TenantEntity>()
                        .eq("id", id)
                        .eq("mark", 1)
                        .set("status", 0)
                        .set("update_user", "platform-admin")
                        .set("update_time", LocalDateTime.now()));
        log.info("[tenant] suspended tenant '{}' (id={})", row.getTenantCode(), id);
    }

    /**
     * Resume a previously suspended tenant: status=1 and the Keycloak
     * realm re-enabled so logins work again.
     */
    @Transactional
    public void resume(String id) {
        TenantEntity row = requireActiveTenantNotBuiltIn(id, "resume");
        if (Integer.valueOf(1).equals(row.getStatus())) {
            return; // already active — idempotent
        }
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.enableRealm(row.getTenantCode());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not enable realm in Keycloak: " + e.getMessage());
            }
        }
        tenantMapper.update(null,
                new UpdateWrapper<TenantEntity>()
                        .eq("id", id)
                        .eq("mark", 1)
                        .set("status", 1)
                        .set("update_user", "platform-admin")
                        .set("update_time", LocalDateTime.now()));
        log.info("[tenant] resumed tenant '{}' (id={})", row.getTenantCode(), id);
    }

    private TenantEntity requireActiveTenantNotBuiltIn(String id, String op) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }
        if (RESERVED_CODES.contains(row.getTenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in tenant '" + row.getTenantCode() + "' cannot be " + op + "ed");
        }
        return row;
    }

    /**
     * Soft-delete a tenant: disable the Keycloak realm (users can't sign
     * in any more) and mark the DB row deleted. Business data
     * ({@code core_auth_user}, etc. with this tenant_code) stays
     * untouched so a future "undo" or hard-purge has its substrate.
     */
    @Transactional
    public void softDelete(String id) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }
        if (RESERVED_CODES.contains(row.getTenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in tenant '" + row.getTenantCode() + "' cannot be deleted");
        }

        // KC first so we know the IdP can be reached BEFORE we mark the
        // row dead. If KC is down we'd rather fail-loud and let the
        // operator retry once it's back, than mark dead in DB but leave
        // the realm enabled (a state that lets users keep signing in
        // even though the registry says the tenant is gone).
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.disableRealm(row.getTenantCode());
            } catch (Exception e) {
                log.warn("[tenant] disableRealm for '{}' failed: {}",
                        row.getTenantCode(), e.toString());
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not disable realm in Keycloak: " + e.getMessage());
            }
        }

        // Then the registry row. UpdateWrapper because mark is @TableLogic
        // — updateById would silently strip mark from the SET clause.
        tenantMapper.update(null,
                new UpdateWrapper<TenantEntity>()
                        .eq("id", id)
                        .eq("mark", 1)
                        .set("mark", 0)
                        .set("update_user", "platform-admin")
                        .set("update_time", LocalDateTime.now()));

        log.info("[tenant] soft-deleted tenant '{}' (id={})", row.getTenantCode(), id);
    }

    private TenantDto.View toView(TenantEntity e) {
        return new TenantDto.View(
                e.getId(),
                e.getTenantCode(),
                e.getDisplayName(),
                e.getContactEmail(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime()
        );
    }
}
