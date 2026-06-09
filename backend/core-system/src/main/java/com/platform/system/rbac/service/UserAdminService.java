package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.dict.CommonStatus;
import com.platform.core.common.dict.DictEnum;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.system.auth.service.SessionTerminationService;
import com.platform.core.infrastructure.security.PasswordPolicyService;
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

    public PageResult<UserDto.View> list(long page, long size, String keyword, String deptId) {
        Page<UserEntity> p = new Page<>(page, size);
        QueryWrapper<UserEntity> w = new QueryWrapper<UserEntity>().eq("mark", 1).orderByDesc("create_time");
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("username", keyword).or().like("email", keyword).or().like("display_name", keyword));
        }
        if (deptId != null && !deptId.isBlank()) {
            w.eq("dept_id", deptId);
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

        userMapper.insert(u);

        // Side-effect: notification email. INVITE includes the magic link;
        // DIRECT just confirms account opening + reminds of the initial creds.
        // Failures here are LOGGED, not propagated — the user row is already
        // committed, and a missing email is recoverable (admin can resend).
        notifyOnboarding(u, mode, req.password(), tenantId);

        return u.getId();
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
                model.put("expiresIn", "7");
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

    @Transactional
    public void update(String id, UserDto.UpdateRequest req) {
        UserEntity u = require(id);
        // "Protected admin" = the built-in admin OR the tenant's singular
        // SUPER_ADMIN. Both are partially editable: contact fields (email,
        // displayName) are allowed because break-glass alerts need a reachable
        // inbox and a recognisable sender name. Structural fields (deptId,
        // status) stay locked — changing them would break invariants the rest
        // of the codebase depends on (e.g. disabling the only super-admin would
        // lock everyone out). Roles are gated in assignRoles; delete /
        // changeStatus / force-logout block these accounts wholesale.
        boolean locked = isBuiltInAdmin(u) || isTenantSuperAdmin(u);
        if (locked) {
            if (req.deptId() != null && !java.util.Objects.equals(req.deptId(), u.getDeptId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.adminContactOnly");
            }
            if (req.status() != null && !java.util.Objects.equals(req.status(), u.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.adminContactOnly");
            }
        }
        if (req.email() != null) u.setEmail(req.email());
        // userNo は採番（read-only）。クライアントから来ても無視（DTO にも無い）。
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        if (!locked) {
            if (req.deptId() != null) u.setDeptId(req.deptId());
            if (req.status() != null) {
                DictEnum.requireValid(CommonStatus.class, req.status(), "status");
                u.setStatus(req.status());
            }
        }
        userMapper.updateById(u);
        cacheService.evictUser(id);
    }

    @Transactional
    public void delete(String id) {
        UserEntity u = require(id);
        assertNotBuiltInAdmin(u, "delete");
        assertNotLastSuperAdmin(id, "delete");
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
        return userRoleMapper.selectList(
                new QueryWrapper<UserRoleEntity>().eq("user_id", userId).eq("mark", 1))
                .stream().map(UserRoleEntity::getRoleId).toList();
    }

    @Transactional
    public void assignRoles(String userId, List<String> roleIds) {
        UserEntity u = require(userId);
        assertNotBuiltInAdmin(u, "assign roles");
        // SUPER_ADMIN is singular and locked to the user invited at tenant
        // creation. Two directions are enforced so the UI can't bypass either:
        //  - it can NEVER be granted to a second user (no transfer) — the tenant
        //    keeps exactly one super admin, the original invitee;
        //  - the sole holder can never have it stripped (would strand the tenant
        //    with zero super admins) — same invariant delete/disable enforce.
        String tid = RequestContext.tenantIdOrDefault();
        String superRoleId = roleLookup.superAdminRoleId(tid);
        if (superRoleId != null) {
            boolean alreadyHolds = userRoleMapper.existsActiveLink(userId, superRoleId, tid) != null;
            boolean wantsSuper = roleIds != null && roleIds.contains(superRoleId);
            if (wantsSuper && !alreadyHolds) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.superAdminSingleton");
            }
            if (alreadyHolds && !wantsSuper) {
                assertNotLastSuperAdmin(userId, "strip SUPER_ADMIN from");
            }
        }
        userRoleMapper.update(null,
                new UpdateWrapper<UserRoleEntity>().eq("user_id", userId).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        if (roleIds != null) {
            for (String roleId : roleIds) {
                UserRoleEntity link = new UserRoleEntity();
                link.setUserId(userId);
                link.setRoleId(roleId);
                userRoleMapper.insert(link);
            }
        }
        cacheService.evictUser(userId);
    }

    /**
     * Force a user's sessions to end (kick) — but refuse for a protected admin
     * (built-in admin or the tenant's SUPER_ADMIN), who must stay reachable in
     * the management UI. The {@code /admin/auth/force-logout} endpoint delegates
     * here so the guard lives in the service (Hard Rule 12), not the controller.
     */
    public void forceLogout(String userId) {
        UserEntity u = require(userId);
        if (isBuiltInAdmin(u) || isTenantSuperAdmin(u)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.adminProtected");
        }
        sessionTermination.terminateUser(userId);
    }

    @Transactional
    public void changeDept(String userId, String deptId) {
        UserEntity u = require(userId);
        assertNotBuiltInAdmin(u, "change dept");
        u.setDeptId(deptId);
        userMapper.updateById(u);
        cacheService.evictUser(userId);
    }

    @Transactional
    public void changeStatus(String userId, int status) {
        DictEnum.requireValid(CommonStatus.class, status, "status");
        UserEntity u = require(userId);
        assertNotBuiltInAdmin(u, "change status");
        boolean enabling = status == CommonStatus.ENABLED.code();
        // Only the "disable" direction can strand the platform without a super
        // admin; enabling a previously-disabled super-admin is always safe.
        if (!enabling) {
            assertNotLastSuperAdmin(userId, "disable");
        }
        u.setStatus(status);
        userMapper.updateById(u);
        cacheService.evictUser(userId);
        // All session/Keycloak side-effects of the enabled state live in one place
        // (shared with the platform-user console): disable kicks tokens + disables
        // the KC user (KC refuses the login — a DB status=0 alone does NOT stop SSO)
        // + ends the KC session; enable clears the kick + re-enables the KC user.
        sessionTermination.applyEnabled(userId, enabling);
    }

    /**
     * The default {@code admin} user is the project's "built-in" identity: it owns SUPER_ADMIN
     * and is hardcoded in {@code LocalAdminSeeder}. We refuse to mutate its record / role-binding
     * / status / dept through the admin API. Password resets are still allowed (they go through
     * {@code AdminAuthController.resetPassword}, not this service).
     */
    private void assertNotBuiltInAdmin(UserEntity u, String op) {
        if (isBuiltInAdmin(u)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in admin user is read-only — only password reset is allowed (rejected: " + op + ")");
        }
    }

    private static final String BUILTIN_ADMIN_USERNAME = "demo-admin";

    /**
     * Refuse an operation if {@code userId} is the only active holder of the
     * {@code SUPER_ADMIN} role <em>in the caller's tenant</em>. Without this
     * guard a single careless delete / disable / role-strip leaves the
     * tenant with zero usable super admins and a tedious DB-fix recovery.
     */
    private void assertNotLastSuperAdmin(String userId, String op) {
        String tid = RequestContext.tenantIdOrDefault();
        String superRoleId = roleLookup.superAdminRoleId(tid);
        if (superRoleId == null) return; // tenant has no built-in super admin row → nothing to guard
        if (userRoleMapper.existsActiveLink(userId, superRoleId, tid) == null) return; // not a super admin
        Long total = userRoleMapper.countActiveHoldersByRoleId(superRoleId, tid);
        if (total != null && total <= 1L) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Cannot " + op + " the last active SUPER_ADMIN user");
        }
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
