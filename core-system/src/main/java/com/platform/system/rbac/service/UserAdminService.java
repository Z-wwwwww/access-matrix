package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.dto.UserDto;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAdminService {

    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder encoder;
    private final PasswordPolicyService passwordPolicy;
    private final PermissionCacheService cacheService;
    private final ForceLogoutService forceLogoutService;

    public UserAdminService(UserMapper userMapper,
                            UserRoleMapper userRoleMapper,
                            RoleMapper roleMapper,
                            PasswordEncoder encoder,
                            PasswordPolicyService passwordPolicy,
                            PermissionCacheService cacheService,
                            ForceLogoutService forceLogoutService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.cacheService = cacheService;
        this.forceLogoutService = forceLogoutService;
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
        passwordPolicy.validate(req.password());
        Long dup = userMapper.selectCount(new QueryWrapper<UserEntity>().eq("mark", 1).eq("username", req.username()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Username already exists: " + req.username());
        }
        UserEntity u = new UserEntity();
        u.setId(IdGenerator.ulid());
        u.setUsername(req.username());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setEmail(req.email());
        u.setUserNo(req.userNo());
        u.setDisplayName(req.displayName());
        u.setDeptId(req.deptId());
        u.setStatus(req.status() == null ? 1 : req.status());
        // Deprecated JSONB columns — keep empty defaults so MyBatis-Plus's NOT_NULL strategy still picks them up.
        u.setRoles("[]");
        u.setAuthorities("[]");
        userMapper.insert(u);
        return u.getId();
    }

    @Transactional
    public void update(String id, UserDto.UpdateRequest req) {
        UserEntity u = require(id);
        assertNotBuiltInAdmin(u, "update");
        if (req.email() != null) u.setEmail(req.email());
        if (req.userNo() != null) u.setUserNo(req.userNo());
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        if (req.deptId() != null) u.setDeptId(req.deptId());
        if (req.status() != null) u.setStatus(req.status());
        userMapper.updateById(u);
        cacheService.evictUser(id);
    }

    @Transactional
    public void delete(String id) {
        UserEntity u = require(id);
        assertNotBuiltInAdmin(u, "delete");
        assertNotLastSuperAdmin(id, "delete");
        u.setMark(0);
        userMapper.updateById(u);
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
                && userRoleMapper.existsActiveLink(userId, superRoleId) != null
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
        if (userRoleMapper.existsActiveLink(userId, superRoleId) == null) return; // not a super admin
        Long total = userRoleMapper.countActiveHoldersByRoleId(superRoleId);
        if (total != null && total <= 1L) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Cannot " + op + " the last active SUPER_ADMIN user");
        }
    }

    private String findSuperAdminRoleId() {
        RoleEntity r = roleMapper.selectOne(
                new QueryWrapper<RoleEntity>()
                        .eq("mark", 1)
                        .eq("code", SUPER_ADMIN_CODE)
                        .last("LIMIT 1"));
        return r == null ? null : r.getId();
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
