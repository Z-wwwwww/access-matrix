package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.dto.UserDto;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAdminService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder encoder;
    private final PasswordPolicyService passwordPolicy;
    private final PermissionCacheService cacheService;

    public UserAdminService(UserMapper userMapper,
                            UserRoleMapper userRoleMapper,
                            PasswordEncoder encoder,
                            PasswordPolicyService passwordPolicy,
                            PermissionCacheService cacheService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.cacheService = cacheService;
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
        if ("admin".equals(u.getUsername())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "The default admin user cannot be deleted");
        }
        u.setMark(0);
        userMapper.updateById(u);
        userRoleMapper.update(null,
                new UpdateWrapper<UserRoleEntity>().eq("user_id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        cacheService.evictUser(id);
    }

    public List<String> listRoleIds(String userId) {
        require(userId);
        return userRoleMapper.selectList(
                new QueryWrapper<UserRoleEntity>().eq("user_id", userId).eq("mark", 1))
                .stream().map(UserRoleEntity::getRoleId).toList();
    }

    @Transactional
    public void assignRoles(String userId, List<String> roleIds) {
        require(userId);
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
        u.setDeptId(deptId);
        userMapper.updateById(u);
        cacheService.evictUser(userId);
    }

    @Transactional
    public void changeStatus(String userId, int status) {
        UserEntity u = require(userId);
        u.setStatus(status);
        userMapper.updateById(u);
        cacheService.evictUser(userId);
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
