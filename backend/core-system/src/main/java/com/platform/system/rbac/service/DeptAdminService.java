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
    public void delete(String id, boolean force) {
        DeptEntity d = require(id);
        long children = countNonNull(deptMapper.selectCount(
                new QueryWrapper<DeptEntity>().eq("mark", 1).eq("parent_id", id)));
        long users = countNonNull(userMapper.selectCount(
                new QueryWrapper<UserEntity>().eq("mark", 1).eq("dept_id", id)));
        // role_dept 経由の参照（SCOPE_CUSTOM の role が ここを範囲に含めている件数）。
        // ここを数えないと、子も user もないが role に引用されている dept が無声で削除され、
        // 該当 role のデータ範囲が静かに狭まる事故になる。
        long roles = countNonNull(roleDeptMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RoleDeptEntity>()
                        .eq("mark", 1).eq("dept_id", id)));
        if ((children > 0 || users > 0 || roles > 0) && !force) {
            // IN_USE — caller may retry with force=true to cascade-clean the subtree.
            // detail map ships structured counts so the frontend can format an i18n message.
            StringBuilder sb = new StringBuilder("Department in use: ");
            boolean first = true;
            if (children > 0) { sb.append(children).append(" sub-department(s)"); first = false; }
            if (users > 0) { if (!first) sb.append(", "); sb.append(users).append(" user(s) assigned"); first = false; }
            if (roles > 0) { if (!first) sb.append(", "); sb.append(roles).append(" role(s) referencing"); }
            throw new BusinessException(ErrorCode.IN_USE, sb.toString(),
                    java.util.Map.of("children", children, "users", users, "roles", roles));
        }
        if (force && (children > 0 || users > 0 || roles > 0)) {
            // Subtree force-delete: soft-delete all descendant depts, null user.dept_id
            // for users in the subtree, and cascade role_dept references.
            java.util.List<String> subtreeIds = deptMapper.findSubtreeIds(d.getPath());
            if (subtreeIds == null || subtreeIds.isEmpty()) {
                // findSubtreeIds filters by status=1; fall back to self if nothing returned.
                subtreeIds = java.util.List.of(id);
            }
            userMapper.update(null,
                    new UpdateWrapper<UserEntity>().in("dept_id", subtreeIds)
                            .set("dept_id", null).set("update_user", "system"));
            roleDeptMapper.update(null,
                    new UpdateWrapper<RoleDeptEntity>().in("dept_id", subtreeIds).eq("mark", 1)
                            .set("mark", 0).set("update_user", "system"));
            deptMapper.update(null,
                    new UpdateWrapper<DeptEntity>().in("id", subtreeIds).eq("mark", 1)
                            .set("mark", 0).set("update_user", "system"));
        } else {
            // No dependencies — simple single-row soft delete + role_dept cascade.
            // 注意：mark は @TableLogic フィールド。BaseMapper.updateById は @TableLogic を
            //   SET 句から自動除外するので、setMark(0)+updateById では mark が変わらない。
            //   明示的に UpdateWrapper で set("mark", 0) する必要がある。
            deptMapper.update(null,
                    new UpdateWrapper<DeptEntity>().eq("id", id).eq("mark", 1)
                            .set("mark", 0).set("update_user", "system"));
            roleDeptMapper.update(null,
                    new UpdateWrapper<RoleDeptEntity>().eq("dept_id", id).eq("mark", 1)
                            .set("mark", 0).set("update_user", "system"));
        }
        cacheService.evictAllDepts();
    }

    private static long countNonNull(Long v) {
        return v == null ? 0L : v;
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
