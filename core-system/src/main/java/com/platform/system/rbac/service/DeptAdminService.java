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
        d.setLeaderUserId(req.leaderUserId());
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
        if (req.leaderUserId() != null) d.setLeaderUserId(req.leaderUserId());
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
}
