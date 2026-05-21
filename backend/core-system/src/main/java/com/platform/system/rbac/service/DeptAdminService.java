package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.dto.DeptAdminDto;
import com.platform.system.rbac.entity.DeptEntity;
import com.platform.system.rbac.entity.RoleDeptEntity;
import com.platform.system.rbac.mapper.DeptMapper;
import com.platform.system.rbac.mapper.RoleDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeptAdminService {

    private final DeptMapper deptMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final UserMapper userMapper;
    private final PermissionCacheService cacheService;

    public DeptAdminService(DeptMapper deptMapper,
                            RoleDeptMapper roleDeptMapper,
                            UserMapper userMapper,
                            PermissionCacheService cacheService) {
        this.deptMapper = deptMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.userMapper = userMapper;
        this.cacheService = cacheService;
    }

    @Transactional
    public String create(DeptAdminDto.CreateRequest req) {
        Long dup = deptMapper.selectCount(new QueryWrapper<DeptEntity>().eq("mark", 1).eq("code", req.code()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Department code already exists: " + req.code());
        }
        DeptEntity parent = null;
        if (req.parentId() != null && !req.parentId().isBlank()) {
            parent = require(req.parentId());
        }
        validateLeaderUserId(req.leaderUserId());
        DeptEntity d = new DeptEntity();
        d.setId(IdGenerator.ulid());
        d.setParentId(parent == null ? null : parent.getId());
        d.setCode(req.code());
        d.setName(req.name());
        d.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        d.setPath(parent == null
                ? "/" + d.getId()
                : parent.getPath() + "/" + d.getId());
        d.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        d.setLeaderUserId(normalizeLeader(req.leaderUserId()));
        d.setStatus(req.status() == null ? 1 : req.status());
        deptMapper.insert(d);
        cacheService.evictAllDepts();
        return d.getId();
    }

    @Transactional
    public void update(String id, DeptAdminDto.UpdateRequest req) {
        DeptEntity d = require(id);
        if (req.parentId() != null && !req.parentId().isBlank() && !req.parentId().equals(d.getParentId())) {
            if (req.parentId().equals(id)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "A department cannot be its own parent");
            }
            // Disallow assigning the parent to one of the dept's own descendants — would create a cycle.
            for (String descendantId : deptMapper.findSubtreeIds(d.getPath())) {
                if (descendantId.equals(req.parentId())) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            "New parent is a descendant — would create a cycle");
                }
            }
            DeptEntity newParent = require(req.parentId());
            d.setParentId(newParent.getId());
            d.setLevel(newParent.getLevel() + 1);
            d.setPath(newParent.getPath() + "/" + d.getId());
            // Note: descendants' paths/levels are not auto-refreshed here. That's a more invasive
            // operation handled by a "rebuild paths" admin command (Stage 5).
        }
        if (req.name() != null) d.setName(req.name());
        if (req.sortOrder() != null) d.setSortOrder(req.sortOrder());
        if (req.leaderUserId() != null) {
            // Empty string is the front-end's "clear the leader" payload; only
            // run the existence probe for a real id.
            String normalized = normalizeLeader(req.leaderUserId());
            if (normalized != null) validateLeaderUserId(normalized);
            d.setLeaderUserId(normalized);
        }
        if (req.status() != null) d.setStatus(req.status());
        deptMapper.updateById(d);
        cacheService.evictAllDepts();
    }

    @Transactional
    public void delete(String id) {
        DeptEntity d = require(id);
        Long children = deptMapper.selectCount(new QueryWrapper<DeptEntity>().eq("mark", 1).eq("parent_id", id));
        if (children != null && children > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Department has children; remove them first");
        }
        Long users = userMapper.selectCount(new QueryWrapper<UserEntity>().eq("mark", 1).eq("dept_id", id));
        if (users != null && users > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Department has users assigned; reassign first");
        }
        d.setMark(0);
        deptMapper.updateById(d);
        roleDeptMapper.update(null,
                new UpdateWrapper<RoleDeptEntity>().eq("dept_id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        cacheService.evictAllDepts();
    }

    private DeptEntity require(String id) {
        DeptEntity d = deptMapper.selectById(id);
        if (d == null || d.getMark() == null || d.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Department not found: " + id);
        }
        return d;
    }

    /**
     * The leader_user_id is non-authorising metadata — see {@code DeptEntity}
     * javadoc. We still validate that the supplied id resolves to an active
     * user so the front-end's dropdown can't smuggle a typo in. Null / blank
     * means "no leader assigned" and is always accepted.
     *
     * <p>Tenant scoping: the MyBatis-Plus tenant interceptor rewrites
     * {@code selectById} to include {@code tenant_id = currentTenant}, so a
     * cross-tenant id is naturally rejected when the interceptor is enabled
     * (dev/prod). In local mode (interceptor off) cross-tenant ids slip
     * through, but local has only the {@code default} tenant anyway.
     */
    private void validateLeaderUserId(String leaderUserId) {
        if (leaderUserId == null || leaderUserId.isBlank()) return;
        UserEntity u = userMapper.selectById(leaderUserId);
        if (u == null || u.getMark() == null || u.getMark() != 1
                || u.getStatus() == null || u.getStatus() != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Leader user id does not point to an active user: " + leaderUserId);
        }
    }

    /** Collapse blank to null so DB stores NULL rather than empty string for "no leader". */
    private static String normalizeLeader(String leaderUserId) {
        return (leaderUserId == null || leaderUserId.isBlank()) ? null : leaderUserId.trim();
    }
}
