package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.rbac.dto.UserDto;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserAdminService {

    /** Numbering definition seeded by V4 migration. Format {@code U[%]} with 8-digit zero-pad. */
    private static final String USER_NO_KBN = "USER";
    private static final String DEFAULT_TENANT = "demo";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder encoder;
    private final PasswordPolicyService passwordPolicy;
    private final PermissionCacheService cacheService;
    private final ForceLogoutService forceLogoutService;
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
                            RoleMapper roleMapper,
                            PasswordEncoder encoder,
                            PasswordPolicyService passwordPolicy,
                            PermissionCacheService cacheService,
                            ForceLogoutService forceLogoutService,
                            NumberingService numberingService,
                            ObjectProvider<KeycloakUserService> keycloakProvider,
                            ObjectProvider<InviteTokenService> inviteProvider,
                            ObjectProvider<MailService> mailProvider,
                            AppMailProperties mailProps) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.cacheService = cacheService;
        this.forceLogoutService = forceLogoutService;
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
        List<UserDto.View> records = result.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public UserDto.View get(String id) {
        return toView(require(id));
    }

    @Transactional
    public String create(UserDto.CreateRequest req) {
        // DIRECT requires a typed-in password (validate complexity + HIBP).
        // INVITE never asks the admin for one — the user picks at acceptance time.
        UserDto.ProvisionMode mode = req.mode();
        if (mode == UserDto.ProvisionMode.DIRECT) {
            if (req.password() == null || req.password().isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Password is required when provision mode is DIRECT");
            }
            passwordPolicy.validate(req.password());
        } else {
            if (req.email() == null || req.email().isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Email is required when provision mode is INVITE");
            }
        }

        Long dup = userMapper.selectCount(new QueryWrapper<UserEntity>().eq("mark", 1).eq("username", req.username()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Username already exists: " + req.username());
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
        u.setStatus(req.status() == null ? 1 : req.status());

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
        // Built-in admin is partially editable: contact fields (email,
        // displayName) are allowed because break-glass alerts need a
        // reachable inbox and a recognisable sender name. Structural
        // fields (deptId, status) stay locked — changing them would
        // break invariants the rest of the codebase depends on (e.g.
        // disabling the only super-admin would lock everyone out).
        // The other mutating paths (delete / changeStatus / assignRoles
        // / changeDept) still call assertNotBuiltInAdmin themselves and
        // remain fully blocked.
        boolean isBuiltIn = BUILTIN_ADMIN_USERNAME.equalsIgnoreCase(u.getUsername());
        if (isBuiltIn) {
            if (req.deptId() != null && !java.util.Objects.equals(req.deptId(), u.getDeptId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Built-in admin user cannot change department");
            }
            if (req.status() != null && !java.util.Objects.equals(req.status(), u.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Built-in admin user cannot change status");
            }
        }
        if (req.email() != null) u.setEmail(req.email());
        // userNo は採番（read-only）。クライアントから来ても無視（DTO にも無い）。
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        if (!isBuiltIn) {
            if (req.deptId() != null) u.setDeptId(req.deptId());
            if (req.status() != null) u.setStatus(req.status());
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
        // naturally expires.
        forceLogoutService.kickOut(id);
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
        // If the new set strips SUPER_ADMIN from a user who currently holds it,
        // and they are the sole super admin left, refuse — same invariant the
        // delete/disable paths enforce.
        String superRoleId = findSuperAdminRoleId();
        if (superRoleId != null
                && userRoleMapper.existsActiveLink(userId, superRoleId, RequestContext.tenantIdOrDefault()) != null
                && (roleIds == null || !roleIds.contains(superRoleId))) {
            assertNotLastSuperAdmin(userId, "strip SUPER_ADMIN from");
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
        UserEntity u = require(userId);
        assertNotBuiltInAdmin(u, "change status");
        // Only the "disable" direction can strand the platform without a super
        // admin; enabling a previously-disabled super-admin is always safe.
        if (status != 1) {
            assertNotLastSuperAdmin(userId, "disable");
        }
        u.setStatus(status);
        userMapper.updateById(u);
        cacheService.evictUser(userId);
        // Disabling a user must take effect immediately for every active token,
        // not just on endpoints that happen to be @RequiresPermission-annotated.
        // The kick combined with the global ForceLogoutFilter shuts down all
        // in-flight sessions on the next API call.
        if (status != 1) {
            forceLogoutService.kickOut(userId);
        }
    }

    /**
     * The default {@code admin} user is the project's "built-in" identity: it owns SUPER_ADMIN
     * and is hardcoded in {@code LocalAdminSeeder}. We refuse to mutate its record / role-binding
     * / status / dept through the admin API. Password resets are still allowed (they go through
     * {@code AdminAuthController.resetPassword}, not this service).
     */
    private void assertNotBuiltInAdmin(UserEntity u, String op) {
        if (BUILTIN_ADMIN_USERNAME.equalsIgnoreCase(u.getUsername())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in admin user is read-only — only password reset is allowed (rejected: " + op + ")");
        }
    }

    private static final String BUILTIN_ADMIN_USERNAME = "admin";

    /**
     * Refuse an operation if {@code userId} is the only active holder of the
     * {@code SUPER_ADMIN} role. Without this guard a single careless delete /
     * disable / role-strip leaves the platform with zero usable super admins
     * and a tedious DB-fix recovery path.
     */
    private void assertNotLastSuperAdmin(String userId, String op) {
        String superRoleId = findSuperAdminRoleId();
        if (superRoleId == null) return; // role row missing entirely → nothing to guard
        String tid = RequestContext.tenantIdOrDefault();
        if (userRoleMapper.existsActiveLink(userId, superRoleId, tid) == null) return; // not a super admin
        Long total = userRoleMapper.countActiveHoldersByRoleId(superRoleId, tid);
        if (total != null && total <= 1L) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Cannot " + op + " the last active SUPER_ADMIN user");
        }
    }

    /**
     * Returns the SUPER_ADMIN role id iff the seeded row is still live. Looking up by the
     * seeded ULID (see {@link BuiltInRoles}) instead of by code/name string means renaming
     * the role's display name does not break the "last super admin" protection.
     */
    private String findSuperAdminRoleId() {
        RoleEntity r = roleMapper.selectById(BuiltInRoles.SUPER_ADMIN_ID);
        if (r == null || !Integer.valueOf(1).equals(r.getMark())) return null;
        return r.getId();
    }

    private UserEntity require(String id) {
        UserEntity u = userMapper.selectById(id);
        if (u == null || u.getMark() == null || u.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found: " + id);
        }
        return u;
    }

    private UserDto.View toView(UserEntity u) {
        return new UserDto.View(
                u.getId(), u.getUsername(), u.getEmail(),
                u.getUserNo(), u.getDisplayName(), u.getDeptId(),
                u.getStatus());
    }
}
