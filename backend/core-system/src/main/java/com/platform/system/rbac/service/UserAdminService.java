package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.dict.CommonStatus;
import com.platform.core.common.dict.DictEnum;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ConcurrentEdit;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.system.auth.service.SessionTerminationService;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.core.infrastructure.security.TempPasswords;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.rbac.dto.UserDto;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserAdminService {

    /** Numbering definition seeded by V4 migration. Format {@code U[%]} with 8-digit zero-pad. */
    private static final String USER_NO_KBN = "USER";
    private static final String DEFAULT_TENANT = "demo";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final BuiltInRoleLookup roleLookup;
    private final PasswordEncoder encoder;
    private final PasswordPolicyService passwordPolicy;
    private final PermissionCacheService cacheService;
    private final SessionTerminationService sessionTermination;
    private final NumberingService numberingService;
    // The following four are only wired when app.security.mode=oidc (the
    // beans are @ConditionalOnProperty). ObjectProvider keeps this service
    // bootable in non-OIDC modes — create(...) checks for null before using.
    private final ObjectProvider<KeycloakUserService> keycloakProvider;
    private final ObjectProvider<InviteTokenService> inviteProvider;
    private final ObjectProvider<MailService> mailProvider;
    private final AppMailProperties mailProps;

    public UserAdminService(UserMapper userMapper,
                            UserRoleMapper userRoleMapper,
                            BuiltInRoleLookup roleLookup,
                            PasswordEncoder encoder,
                            PasswordPolicyService passwordPolicy,
                            PermissionCacheService cacheService,
                            SessionTerminationService sessionTermination,
                            NumberingService numberingService,
                            ObjectProvider<KeycloakUserService> keycloakProvider,
                            ObjectProvider<InviteTokenService> inviteProvider,
                            ObjectProvider<MailService> mailProvider,
                            AppMailProperties mailProps) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleLookup = roleLookup;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.cacheService = cacheService;
        this.sessionTermination = sessionTermination;
        this.numberingService = numberingService;
        this.keycloakProvider = keycloakProvider;
        this.inviteProvider = inviteProvider;
        this.mailProvider = mailProvider;
        this.mailProps = mailProps;
    }

    public PageResult<UserDto.View> list(long page, long size, String keyword, String deptId, String roleId) {
        Page<UserEntity> p = new Page<>(page, size);
        QueryWrapper<UserEntity> w = new QueryWrapper<UserEntity>().eq("mark", 1).orderByDesc("create_time");
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("username", keyword).or().like("email", keyword).or().like("display_name", keyword));
        }
        if (deptId != null && !deptId.isBlank()) {
            w.eq("dept_id", deptId);
        }
        if (roleId != null && !roleId.isBlank()) {
            // Users holding the role via an active link. {0}/{1} are bound
            // parameters (no string concat — roleId is client input). The
            // outer column must be table-qualified: the subquery's own `id`
            // would shadow it. tenant_id is bound explicitly so the filter
            // doesn't depend on the tenant interceptor rewriting subqueries.
            w.exists("SELECT 1 FROM core_rbac_user_role ur WHERE ur.user_id = core_auth_user.id "
                    + "AND ur.role_id = {0} AND ur.tenant_id = {1} AND ur.mark = 1",
                    roleId, RequestContext.tenantIdOrDefault());
        }
        Page<UserEntity> result = userMapper.selectPage(p, w);
        Set<String> superIds = superAdminUserIds();
        List<UserDto.View> records = result.getRecords().stream().map(u -> toView(u, superIds)).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public UserDto.View get(String id) {
        return toView(require(id), superAdminUserIds());
    }

    @Transactional
    public String create(UserDto.CreateRequest req) {
        // DIRECT requires a typed-in password (validate complexity + HIBP).
        // INVITE never asks the admin for one — the user picks at acceptance time.
        UserDto.ProvisionMode mode = req.mode();
        if (mode == UserDto.ProvisionMode.DIRECT) {
            if (req.password() == null || req.password().isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.passwordRequired");
            }
            passwordPolicy.validate(req.password());
        } else {
            if (req.email() == null || req.email().isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.emailRequired");
            }
        }

        // Precise duplicate pre-checks (username + email) BEFORE touching Keycloak —
        // otherwise an email clash surfaces only as KC's generic English CONFLICT.
        // Messages are i18n KEYS so the frontend localizes them (see localizeError).
        // The MP tenant interceptor scopes both counts to the caller's tenant.
        Long usernameDup = userMapper.selectCount(new QueryWrapper<UserEntity>().eq("mark", 1).eq("username", req.username()));
        if (usernameDup != null && usernameDup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.usernameExists");
        }
        if (req.email() != null && !req.email().isBlank()) {
            Long emailDup = userMapper.selectCount(new QueryWrapper<UserEntity>().eq("mark", 1).eq("email", req.email()));
            if (emailDup != null && emailDup > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.emailExists");
            }
        }

        // Tenant for numbering: each tenant has its own user-no counter.
        String tenantId = RequestContext.tenantId();
        if (tenantId == null || tenantId.isBlank()) tenantId = DEFAULT_TENANT;

        UserEntity u = new UserEntity();
        u.setId(IdGenerator.ulid());
        u.setUsername(req.username());
        u.setEmail(req.email());
        // Legacy password column — only filled in DIRECT mode for the
        // (mode=password) fallback path. OIDC users authenticate via Keycloak.
        if (mode == UserDto.ProvisionMode.DIRECT) {
            u.setPasswordHash(encoder.encode(req.password()));
        }
        u.setUserNo(numberingService.next(USER_NO_KBN, tenantId));
        u.setDisplayName(req.displayName());
        u.setDeptId(req.deptId());
        Integer status = req.status() == null ? CommonStatus.ENABLED.code() : req.status();
        DictEnum.requireValid(CommonStatus.class, status, "status");
        u.setStatus(status);

        // Side-effect: provision in Keycloak first when oidc is on, so we can
        // store keycloak_id on the row we insert. On Keycloak failure we never
        // touch the business DB → no half-created users.
        KeycloakUserService keycloak = keycloakProvider.getIfAvailable();
        String kcId = null;
        if (keycloak != null) {
            // For INVITE the Keycloak user has no credentials yet; setPassword
            // is called from the invite-acceptance endpoint after the user
            // chooses their password.
            String tempPw = (mode == UserDto.ProvisionMode.DIRECT) ? req.password() : null;
            kcId = keycloak.createUser(tenantId, req.username(), req.email(), req.displayName(), tempPw);
            u.setKeycloakId(kcId);
        }

        try {
            userMapper.insert(u);
        } catch (RuntimeException e) {
            // COMPENSATION. The KC user is already created and this @Transactional
            // method is about to roll back, so without this the KC user survives as
            // an orphan with NO business row. The operator then can't recover from
            // the UI: the DB pre-checks pass (no row exists), but KC answers 409 and
            // surfaces as "Keycloak user already exists (username or email already
            // in use)" — a confusing error for a user that, as far as the app is
            // concerned, doesn't exist. Both sibling provisioning flows already do
            // this (TenantAdminService.create deletes the realm,
            // PlatformUserAdminService.create deletes the KC user); this was the
            // only one of the three without it.
            compensateKeycloakUser(keycloak, tenantId, kcId, req.username(), e);
            throw e;
        }

        // Side-effect: notification email. INVITE includes the magic link;
        // DIRECT just confirms account opening + reminds of the initial creds.
        // Failures here are LOGGED, not propagated — the user row is already
        // committed, and a missing email is recoverable (admin can resend).
        notifyOnboarding(u, mode, req.password(), tenantId);

        return u.getId();
    }

    /**
     * Remove the just-created Keycloak user after the DB half of
     * {@link #create} failed, so a retry isn't blocked by KC's 409. Best-effort:
     * if compensation itself fails we log LOUDLY with both causes — an orphan KC
     * user then needs manual cleanup, same posture as
     * {@code TenantAdminService.create}.
     */
    private void compensateKeycloakUser(KeycloakUserService keycloak, String tenantId,
                                        String kcId, String username, RuntimeException cause) {
        if (keycloak == null || kcId == null || kcId.isBlank()) return;
        var log = org.slf4j.LoggerFactory.getLogger(UserAdminService.class);
        try {
            keycloak.deleteUser(tenantId, kcId);
            log.warn("[user] create failed for '{}' — compensated by deleting the orphan KC user {}",
                    username, kcId, cause);
        } catch (RuntimeException ce) {
            log.error("[user] create failed for '{}' AND compensation (KC delete of {}) failed — "
                    + "manual cleanup of the Keycloak user is required. Original cause below.",
                    username, kcId, cause);
            log.error("[user] compensation failure detail", ce);
        }
    }

    private void notifyOnboarding(UserEntity u, UserDto.ProvisionMode mode, String tempPassword, String tenantId) {
        MailService mail = mailProvider.getIfAvailable();
        if (mail == null || u.getEmail() == null || u.getEmail().isBlank()) {
            // No mail service wired (legacy / password-only deployments) or no
            // address to send to. Nothing to do — the user was still created.
            return;
        }
        // Recipient locale: take the admin's current locale as a reasonable
        // proxy. The new user has no profile row to query yet, and admins
        // typically invite people who share their language environment.
        // When OIDC users start switching their own locale in the Keycloak
        // account console, the next email (e.g. password reset) will reflect
        // that automatically — MailService reads it from the JWT 'locale'
        // claim through RequestContext.
        java.util.Locale locale = RequestContext.locale();
        if (locale == null) locale = java.util.Locale.JAPAN;

        try {
            Map<String, Object> model = new HashMap<>();
            model.put("appName",     mailProps.fromName());
            model.put("username",    u.getUsername());
            model.put("displayName", u.getDisplayName());
            model.put("tenantId",    tenantId);
            model.put("supportEmail", mailProps.from());

            Object[] subjectArgs = new Object[] { "[" + mailProps.fromName() + "]" };

            if (mode == UserDto.ProvisionMode.INVITE) {
                InviteTokenService invites = inviteProvider.getIfAvailable();
                if (invites == null) {
                    // OIDC off but INVITE requested — shouldn't happen given the
                    // validation in create(), but guard anyway so we never email
                    // a "click here" link that has no backing token.
                    return;
                }
                String token = invites.mint(tenantId, u.getId(), u.getKeycloakId());
                String url = mailProps.baseUrl() + "/invite/" + token;
                model.put("inviteUrl", url);
                // From the configured app.invite.token-ttl, not a literal — the other
                // two invite emails (TenantAdminService / PlatformUserAdminService)
                // already derive it, and a stale "7" misstates the deadline the
                // recipient acts on once the TTL is changed.
                model.put("expiresIn", String.valueOf(invites.ttlDays()));
                mail.sendHtmlAsync(u.getEmail(), locale,
                        "user-invite.subject", subjectArgs,
                        "user-invite", model);
            } else {
                model.put("loginUrl",     mailProps.baseUrl() + "/login");
                model.put("tempPassword", tempPassword);
                mail.sendHtmlAsync(u.getEmail(), locale,
                        "user-direct-welcome.subject", subjectArgs,
                        "user-direct-welcome", model);
            }
        } catch (Exception e) {
            // Anything that goes wrong building / sending the mail is
            // logged at WARN — the user row is already committed.
            // No throw: don't surface an SMTP misconfiguration as a 500 to
            // the admin's user-creation form.
            org.slf4j.LoggerFactory.getLogger(UserAdminService.class)
                    .warn("[user] onboarding mail to {} failed: {}", u.getEmail(), e.toString());
        }
    }

    /**
     * Self-service profile edit from the Profile page — the caller updates
     * their OWN contact fields (email / display name) only. This is the
     * sanctioned path for self-edit: the admin user-management console refuses
     * any operation on your own account (see {@link #assertNotSelf}), so this
     * is the one place a user changes their own info. Dept / status / roles are
     * never touched here.
     */
    @Transactional
    public void updateOwnProfile(UserDto.ProfileUpdateRequest req) {
        String me = RequestContext.userId();
        if (me == null || me.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        UserEntity u = require(me);
        assertEmailAvailable(req.email(), me);
        syncKeycloakProfile(u, req.email(), req.displayName());
        if (req.email() != null) u.setEmail(req.email());
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        ConcurrentEdit.requireApplied(userMapper.updateById(u));
        cacheService.evictUser(me);
    }

    @Transactional
    public void update(String id, UserDto.UpdateRequest req) {
        assertNotSelf(id);
        UserEntity u = require(id);
        // Protected admins (built-in admin / tenant SUPER_ADMIN) are fully
        // read-only in this console — holding user:update does NOT extend to
        // the tenant's administrator account. The admin edits their own
        // contact info via the Profile page (updateOwnProfile); structural
        // fields (dept / status / roles) never change through any path.
        assertNotProtectedAdmin(u);
        assertEmailAvailable(req.email(), id);
        syncKeycloakProfile(u, req.email(), req.displayName());
        if (req.email() != null) u.setEmail(req.email());
        // userNo は採番（read-only）。クライアントから来ても無視（DTO にも無い）。
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        if (req.deptId() != null) u.setDeptId(req.deptId());
        if (req.status() != null) {
            DictEnum.requireValid(CommonStatus.class, req.status(), "status");
            u.setStatus(req.status());
        }
        ConcurrentEdit.requireApplied(userMapper.updateById(u));
        cacheService.evictUser(id);
    }

    @Transactional
    public void delete(String id) {
        assertNotSelf(id);
        UserEntity u = require(id);
        assertNotProtectedAdmin(u);
        // mark は @TableLogic — BaseMapper.updateById では SET 句から除外されるので UpdateWrapper で明示。
        userMapper.update(null,
                new UpdateWrapper<UserEntity>().eq("id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        userRoleMapper.update(null,
                new UpdateWrapper<UserRoleEntity>().eq("user_id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        cacheService.evictUser(id);
        // Any access token still in flight must die — without this kick a
        // deleted user could keep hitting /menu/me etc. until their token
        // naturally expires. terminateUser also ends the KC SSO session so the
        // deleted user can't be silently re-authenticated on the login redirect.
        sessionTermination.terminateUser(id);
        // Remove the Keycloak user too. Otherwise a soft-deleted user (mark=0)
        // could SSO back in and the JIT resolver — which only sees mark=1 rows —
        // would RE-PROVISION a brand-new account for them. Best-effort: the DB
        // row is already gone, so a KC hiccup just leaves an orphan to clean up.
        KeycloakUserService keycloak = keycloakProvider.getIfAvailable();
        if (keycloak != null && u.getKeycloakId() != null && !u.getKeycloakId().isBlank()) {
            try {
                keycloak.deleteUser(RequestContext.tenantIdOrDefault(), u.getKeycloakId());
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(UserAdminService.class)
                        .warn("[user] soft-deleted {} but KC delete failed (kcId={}): {}",
                                id, u.getKeycloakId(), e.toString());
            }
        }
    }

    public List<String> listRoleIds(String userId) {
        require(userId);
        // JOIN to role.mark=1 + role.tenant_id — never return dangling links to
        // soft-deleted (or another tenant's) roles, which would otherwise surface
        // as "ghost selections" in the assignment dialog and trip the existence
        // check on save. Same rationale as RoleAdminService.listPermissionIds.
        return userRoleMapper.findActiveRoleIdsByUserId(userId, RequestContext.tenantIdOrDefault());
    }

    @Transactional
    public void assignRoles(String userId, List<String> roleIds) {
        assertNotSelf(userId);
        UserEntity u = require(userId);
        // The SUPER_ADMIN holder is a protected admin → fully read-only here,
        // which already covers the "never strip SUPER_ADMIN from the sole
        // holder" direction. The remaining direction: SUPER_ADMIN can never be
        // granted to a second user (no transfer) — the tenant keeps exactly
        // one super admin, the original invitee.
        assertNotProtectedAdmin(u);
        String tid = RequestContext.tenantIdOrDefault();
        String superRoleId = roleLookup.superAdminRoleId(tid);
        if (superRoleId != null && roleIds != null && roleIds.contains(superRoleId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.superAdminSingleton");
        }
        // Sanitise ONCE and drive both the validation and the writes off the same
        // set. Previously assertRolesExist deduped + dropped null/blank internally
        // while the insert loop below iterated the RAW request list, so the two
        // disagreed about what was being written and three malformed bodies sailed
        // past validation straight into a DB error surfaced as a 500 (all verified
        // against the real DB):
        //   ["R1","R1"] → duplicate key value violates uk_core_rbac_user_role
        //   ["R1",null] → null value in column "role_id" violates not-null
        //   ["R1",""]   → violates fk_core_rbac_user_role_role
        // The three sibling bind methods in RoleAdminService never had this gap —
        // they run every id through dedupOrEmpty first.
        LinkedHashSet<String> dedup = dedupOrEmpty(roleIds);
        assertRolesExist(dedup, tid);
        userRoleMapper.update(null,
                new UpdateWrapper<UserRoleEntity>().eq("user_id", userId).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        for (String roleId : dedup) {
            UserRoleEntity link = new UserRoleEntity();
            link.setUserId(userId);
            link.setRoleId(roleId);
            userRoleMapper.insert(link);
        }
        cacheService.evictUser(userId);
    }

    /**
     * Drop nulls / blanks and collapse duplicates, preserving order. Mirrors
     * {@code RoleAdminService.dedupOrEmpty} — the junction tables all carry a
     * {@code (tenant_id, …) WHERE mark = 1} unique index plus NOT NULL and FK
     * constraints, so a raw caller-supplied list must never reach an insert loop.
     */
    private static LinkedHashSet<String> dedupOrEmpty(List<String> ids) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (ids == null) return out;
        for (String id : ids) {
            if (id != null && !id.isBlank()) out.add(id);
        }
        return out;
    }

    /**
     * Every supplied role id must be a live role <b>of this tenant</b>. Without
     * this the insert below relies on the DB: a non-existent id trips the
     * {@code fk_core_rbac_user_role_role} FK and surfaces as a 500 "Unhandled
     * exception" instead of a clean 400, while a soft-deleted or ANOTHER TENANT's
     * role id satisfies the FK (it only references {@code core_rbac_role(id)},
     * not the tenant) and inserts a junk link. Such a link grants nothing — the
     * permission / menu / data-scope joins are all tenant-scoped and fail closed —
     * but it lingers in {@code core_rbac_user_role} and shows up as a checked-but-
     * unknown role in the UI. Same guard, same reasoning as
     * {@code RoleAdminService.assertAllExist}.
     */
    private void assertRolesExist(Set<String> dedup, String tenantId) {
        // Caller sanitises (see assignRoles) — this method must validate exactly
        // the set that gets written, never a differently-filtered view of it.
        if (dedup.isEmpty()) return;
        Long found = userRoleMapper.countLiveRoles(dedup, tenantId);
        if (found == null || found.intValue() != dedup.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.roleNotFound");
        }
    }

    /**
     * Force a user's sessions to end (kick) — but refuse for a protected admin
     * (built-in admin or the tenant's SUPER_ADMIN), who must stay reachable in
     * the management UI. The {@code /admin/auth/force-logout} endpoint delegates
     * here so the guard lives in the service (Hard Rule 12), not the controller.
     */
    public void forceLogout(String userId) {
        assertNotSelf(userId);
        UserEntity u = require(userId);
        assertNotProtectedAdmin(u);
        sessionTermination.terminateUser(userId);
    }

    /**
     * Admin-side password reset — the SAME flow as the platform-user console
     * ({@code PlatformUserAdminService.resetPassword}): rotate to a generated
     * single-use temporary password, terminate the user's sessions, best-effort
     * email the new credentials, and return the temp password ONCE so the admin
     * can hand it over out-of-band. The admin never types a password.
     *
     * <p>In OIDC mode the temp password is written to Keycloak (temporary=true,
     * so KC forces the user to choose their own on next login) — the local
     * {@code password_hash} is never touched, preserving the "as-if-always-OIDC"
     * invariant. In legacy password mode it lands in {@code password_hash}
     * instead (no force-change mechanism exists there).
     *
     * <p>Protected admins (built-in / tenant SUPER_ADMIN) are refused like every
     * other mutation in this console; they recover their own credential via the
     * KC self-service reset or break-glass.
     */
    public UserDto.ResetPwResponse resetPassword(String id) {
        assertNotSelf(id);
        UserEntity u = require(id);
        assertNotProtectedAdmin(u);
        String tempPassword = TempPasswords.generate();
        KeycloakUserService keycloak = keycloakProvider.getIfAvailable();
        if (keycloak != null) {
            if (u.getKeycloakId() == null || u.getKeycloakId().isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.noKeycloakLink");
            }
            keycloak.setPassword(RequestContext.tenantIdOrDefault(), u.getKeycloakId(), tempPassword, true);
        } else {
            u.setPasswordHash(encoder.encode(tempPassword));
            userMapper.updateById(u);
        }
        // The reset must evict the (possibly hijacked) current holder: kick
        // already-issued tokens AND end the KC SSO session, otherwise a login
        // redirect would silently re-authenticate without the new password.
        sessionTermination.terminateUser(id);
        boolean emailSent = sendResetMail(u, tempPassword);
        return new UserDto.ResetPwResponse(u.getUsername(), tempPassword, emailSent);
    }

    /**
     * Best-effort "your password was reset" mail carrying the login URL +
     * username + temp password — the same shared {@code user-direct-welcome}
     * template (with {@code reset=true} wording) the platform-user console
     * sends. A mail failure must not fail the reset: the admin still sees the
     * temp password once in the UI and can hand it over out-of-band.
     */
    private boolean sendResetMail(UserEntity u, String tempPassword) {
        MailService mail = mailProvider.getIfAvailable();
        if (mail == null || u.getEmail() == null || u.getEmail().isBlank()) {
            return false;
        }
        try {
            java.util.Locale locale = RequestContext.locale();
            if (locale == null) locale = java.util.Locale.JAPAN;
            Map<String, Object> model = new HashMap<>();
            model.put("appName",      mailProps.fromName());
            model.put("username",     u.getUsername());
            model.put("displayName",  u.getDisplayName());
            model.put("tenantId",     RequestContext.tenantIdOrDefault());
            model.put("supportEmail", mailProps.from());
            model.put("loginUrl",     mailProps.baseUrl() + "/login");
            model.put("tempPassword", tempPassword);
            model.put("reset", true);   // template switches headline + body wording on this flag
            mail.sendHtmlAsync(u.getEmail(), locale,
                    "user-account-reset.subject", new Object[] { "[" + mailProps.fromName() + "]" },
                    "user-direct-welcome", model);
            return true;
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(UserAdminService.class)
                    .warn("[user] reset-credentials mail to {} failed: {}", u.getEmail(), e.toString());
            return false;
        }
    }

    @Transactional
    public void changeDept(String userId, String deptId) {
        assertNotSelf(userId);
        UserEntity u = require(userId);
        assertNotProtectedAdmin(u);
        u.setDeptId(deptId);
        ConcurrentEdit.requireApplied(userMapper.updateById(u));
        cacheService.evictUser(userId);
    }

    @Transactional
    public void changeStatus(String userId, int status) {
        assertNotSelf(userId);
        DictEnum.requireValid(CommonStatus.class, status, "status");
        UserEntity u = require(userId);
        assertNotProtectedAdmin(u);
        boolean enabling = status == CommonStatus.ENABLED.code();
        u.setStatus(status);
        ConcurrentEdit.requireApplied(userMapper.updateById(u));
        cacheService.evictUser(userId);
        // All session/Keycloak side-effects of the enabled state live in one place
        // (shared with the platform-user console): disable kicks tokens + disables
        // the KC user (KC refuses the login — a DB status=0 alone does NOT stop SSO)
        // + ends the KC session; enable clears the kick + re-enables the KC user.
        sessionTermination.applyEnabled(userId, enabling);
    }

    /**
     * "Protected admin" = the built-in admin ({@code LocalAdminSeeder}'s
     * hardcoded identity) OR the tenant's singular SUPER_ADMIN (the user
     * invited at tenant creation). Both are <b>fully read-only</b> in the
     * user-management console: no other user — whatever user-management
     * permission they hold — may edit, re-role, move, disable, delete,
     * kick, or reset them. The admin manages their own contact info on the
     * Profile page ({@link #updateOwnProfile}) and recovers credentials via
     * the KC self-service reset or break-glass. This also subsumes the old
     * "last active SUPER_ADMIN" guard: the sole super admin can never be
     * deleted / disabled / stripped, so the tenant always keeps one.
     */
    private void assertNotProtectedAdmin(UserEntity u) {
        if (isBuiltInAdmin(u) || isTenantSuperAdmin(u)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.adminProtected");
        }
    }

    private static final String BUILTIN_ADMIN_USERNAME = "demo-admin";

    /**
     * Same precise duplicate pre-check as {@code create}, excluding the row
     * being edited. Email doubles as a login identifier
     * ({@code findByIdentifier} matches {@code email = ? … LIMIT 1}), so a
     * duplicate would make the email-login resolution ambiguous — in OIDC
     * mode Keycloak also rejects it ({@code duplicateEmailsAllowed=false}),
     * but only with a generic error and not at all in legacy password mode.
     * The MP tenant interceptor scopes the count to the caller's tenant.
     */
    private void assertEmailAvailable(String email, String selfId) {
        if (email == null || email.isBlank()) return;
        Long dup = userMapper.selectCount(new QueryWrapper<UserEntity>()
                .eq("mark", 1).eq("email", email).ne("id", selfId));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.emailExists");
        }
    }

    /**
     * Mirror an email / displayName change into Keycloak (same as the
     * platform-user console's {@code kc.updateProfile}): email stays
     * verified, first/last name re-derived. Without this, KC keeps the
     * OLD values — its account console shows them, and worse, the
     * "forgot password" flow mails the OLD address.
     *
     * <p>Called BEFORE the local write, KC-first like {@code create}: a KC
     * failure propagates (mapped to {@code error.keycloak.operationFailed})
     * and the DB row stays untouched, so the two never diverge. No-op when
     * KC is off, the row has no Keycloak link, or both fields are absent.
     */
    private void syncKeycloakProfile(UserEntity u, String email, String displayName) {
        boolean hasEmail   = email != null && !email.isBlank();
        boolean hasDisplay = displayName != null && !displayName.isBlank();
        if (!hasEmail && !hasDisplay) return;
        KeycloakUserService keycloak = keycloakProvider.getIfAvailable();
        if (keycloak == null || u.getKeycloakId() == null || u.getKeycloakId().isBlank()) return;
        keycloak.updateProfile(RequestContext.tenantIdOrDefault(), u.getKeycloakId(), email, displayName);
    }

    private UserEntity require(String id) {
        UserEntity u = userMapper.selectById(id);
        if (u == null || u.getMark() == null || u.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found: " + id);
        }
        return u;
    }

    private UserDto.View toView(UserEntity u, Set<String> superAdminIds) {
        return new UserDto.View(
                u.getId(), u.getUsername(), u.getEmail(),
                u.getUserNo(), u.getDisplayName(), u.getDeptId(),
                u.getStatus(), isBuiltInAdmin(u), superAdminIds.contains(u.getId()));
    }

    /** Whether {@code u} is the platform's read-only built-in admin row. */
    private static boolean isBuiltInAdmin(UserEntity u) {
        return BUILTIN_ADMIN_USERNAME.equalsIgnoreCase(u.getUsername());
    }

    /**
     * Refuse any admin-console mutation that targets the caller's OWN account.
     * Self-service edits go through the Profile page ({@link #updateOwnProfile})
     * instead. This blocks two footguns at once: self privilege-escalation
     * (granting yourself roles) and self-lockout (disabling / deleting / kicking
     * yourself).
     */
    private void assertNotSelf(String targetUserId) {
        String me = RequestContext.userId();
        if (me != null && me.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.selfManagementForbidden");
        }
    }

    /** Ids of the user(s) holding the tenant's SUPER_ADMIN role — singular by design. */
    private Set<String> superAdminUserIds() {
        String tid = RequestContext.tenantIdOrDefault();
        String superRoleId = roleLookup.superAdminRoleId(tid);
        if (superRoleId == null) return Set.of();
        return new HashSet<>(userRoleMapper.findUserIdsByRoleId(superRoleId, tid));
    }

    /** Whether {@code u} holds the tenant's SUPER_ADMIN role. */
    private boolean isTenantSuperAdmin(UserEntity u) {
        String tid = RequestContext.tenantIdOrDefault();
        String superRoleId = roleLookup.superAdminRoleId(tid);
        return superRoleId != null
                && userRoleMapper.existsActiveLink(u.getId(), superRoleId, tid) != null;
    }
}
