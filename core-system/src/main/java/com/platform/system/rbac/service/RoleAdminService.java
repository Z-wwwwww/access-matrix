package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.rbac.dto.RoleDto;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.entity.RoleMenuEntity;
import com.platform.system.rbac.entity.RolePermissionEntity;
import com.platform.system.rbac.entity.RoleDeptEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.mapper.RoleMenuMapper;
import com.platform.system.rbac.mapper.RolePermissionMapper;
import com.platform.system.rbac.mapper.RoleDeptMapper;
import com.platform.core.common.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoleAdminService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final PermissionCacheService cacheService;

    public RoleAdminService(RoleMapper roleMapper,
                            RolePermissionMapper rolePermissionMapper,
                            RoleMenuMapper roleMenuMapper,
                            RoleDeptMapper roleDeptMapper,
                            PermissionCacheService cacheService) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.cacheService = cacheService;
    }

    public PageResult<RoleDto.View> list(long page, long size, String keyword) {
        Page<RoleEntity> p = new Page<>(page, size);
        QueryWrapper<RoleEntity> w = new QueryWrapper<RoleEntity>().eq("mark", 1).orderByAsc("sort_order", "code");
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("code", keyword).or().like("name", keyword));
        }
        Page<RoleEntity> result = roleMapper.selectPage(p, w);
        List<RoleDto.View> records = result.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public RoleDto.View get(String id) {
        RoleEntity r = roleMapper.selectById(id);
        if (r == null || r.getMark() == null || r.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Role not found");
        }
        return toView(r);
    }

    @Transactional
    public String create(RoleDto.CreateRequest req) {
        // Unique code per tenant.
        Long dup = roleMapper.selectCount(new QueryWrapper<RoleEntity>().eq("mark", 1).eq("code", req.code()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Role code already exists: " + req.code());
        }
        RoleEntity r = new RoleEntity();
        r.setId(IdGenerator.ulid());
        r.setCode(req.code());
        r.setName(req.name());
        r.setDescription(req.description());
        r.setDataScope(req.dataScope() == null ? 4 : req.dataScope());
        r.setIsBuiltIn(0);
        r.setStatus(req.status() == null ? 1 : req.status());
        r.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        roleMapper.insert(r);
        return r.getId();
    }

    @Transactional
    public void update(String id, RoleDto.UpdateRequest req) {
        RoleEntity r = requireRole(id);
        if (req.name() != null) r.setName(req.name());
        if (req.description() != null) r.setDescription(req.description());
        if (req.dataScope() != null) r.setDataScope(req.dataScope());
        if (req.sortOrder() != null) r.setSortOrder(req.sortOrder());
        if (req.status() != null) r.setStatus(req.status());
        roleMapper.updateById(r);
        cacheService.evictRole(id);
    }

    @Transactional
    public void delete(String id) {
        RoleEntity r = requireRole(id);
        if (Integer.valueOf(1).equals(r.getIsBuiltIn())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Built-in role cannot be deleted: " + r.getCode());
        }
        r.setMark(0);
        roleMapper.updateById(r);
        // Cascade soft-delete the link rows so future joins ignore them.
        rolePermissionMapper.update(null,
                new UpdateWrapper<RolePermissionEntity>().eq("role_id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        roleMenuMapper.update(null,
                new UpdateWrapper<RoleMenuEntity>().eq("role_id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        roleDeptMapper.update(null,
                new UpdateWrapper<RoleDeptEntity>().eq("role_id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        cacheService.evictRole(id);
    }

    public List<String> listPermissionIds(String roleId) {
        requireRole(roleId);
        return rolePermissionMapper.selectList(
                new QueryWrapper<RolePermissionEntity>().eq("role_id", roleId).eq("mark", 1))
                .stream().map(RolePermissionEntity::getPermissionId).toList();
    }

    @Transactional
    public void bindPermissions(String roleId, List<String> permissionIds) {
        requireRole(roleId);
        rolePermissionMapper.update(null,
                new UpdateWrapper<RolePermissionEntity>().eq("role_id", roleId).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        if (permissionIds != null) {
            for (String permissionId : permissionIds) {
                RolePermissionEntity link = new RolePermissionEntity();
                link.setRoleId(roleId);
                link.setPermissionId(permissionId);
                rolePermissionMapper.insert(link);
            }
        }
        cacheService.evictRole(roleId);
    }

    public List<String> listMenuIds(String roleId) {
        requireRole(roleId);
        return roleMenuMapper.selectList(
                new QueryWrapper<RoleMenuEntity>().eq("role_id", roleId).eq("mark", 1))
                .stream().map(RoleMenuEntity::getMenuId).toList();
    }

    @Transactional
    public void bindMenus(String roleId, List<String> menuIds) {
        requireRole(roleId);
        roleMenuMapper.update(null,
                new UpdateWrapper<RoleMenuEntity>().eq("role_id", roleId).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        if (menuIds != null) {
            for (String menuId : menuIds) {
                RoleMenuEntity link = new RoleMenuEntity();
                link.setRoleId(roleId);
                link.setMenuId(menuId);
                roleMenuMapper.insert(link);
            }
        }
        cacheService.evictRole(roleId);
    }

    public List<String> listDeptIds(String roleId) {
        requireRole(roleId);
        return roleDeptMapper.selectList(
                new QueryWrapper<RoleDeptEntity>().eq("role_id", roleId).eq("mark", 1))
                .stream().map(RoleDeptEntity::getDeptId).toList();
    }

    @Transactional
    public void bindDepts(String roleId, List<String> deptIds) {
        requireRole(roleId);
        roleDeptMapper.update(null,
                new UpdateWrapper<RoleDeptEntity>().eq("role_id", roleId).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        if (deptIds != null) {
            for (String deptId : deptIds) {
                RoleDeptEntity link = new RoleDeptEntity();
                link.setRoleId(roleId);
                link.setDeptId(deptId);
                roleDeptMapper.insert(link);
            }
        }
        cacheService.evictRole(roleId);
    }

    private RoleEntity requireRole(String id) {
        RoleEntity r = roleMapper.selectById(id);
        if (r == null || r.getMark() == null || r.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Role not found: " + id);
        }
        return r;
    }

    private RoleDto.View toView(RoleEntity r) {
        return new RoleDto.View(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getDataScope(), r.getIsBuiltIn(), r.getStatus(), r.getSortOrder());
    }
}
