package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.rbac.dto.RoleDto;
import com.platform.system.rbac.entity.DeptEntity;
import com.platform.system.rbac.entity.MenuEntity;
import com.platform.system.rbac.entity.PermissionEntity;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.entity.RoleMenuEntity;
import com.platform.system.rbac.entity.RolePermissionEntity;
import com.platform.system.rbac.entity.RoleDeptEntity;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.DeptMapper;
import com.platform.system.rbac.mapper.MenuMapper;
import com.platform.system.rbac.mapper.PermissionMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.mapper.RoleMenuMapper;
import com.platform.system.rbac.mapper.RolePermissionMapper;
import com.platform.system.rbac.mapper.RoleDeptMapper;
import com.platform.system.rbac.mapper.UserRoleMapper;
import com.platform.core.common.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RoleAdminService {

    // No more SUPER_ADMIN_CODE — built-in role lookups go through BuiltInRoles ID constants
    // so the user-facing role name can be freely renamed by admins.

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionMapper permissionMapper;
    private final MenuMapper menuMapper;
    private final DeptMapper deptMapper;
    private final PermissionCacheService cacheService;

    public RoleAdminService(RoleMapper roleMapper,
                            RolePermissionMapper rolePermissionMapper,
                            RoleMenuMapper roleMenuMapper,
                            RoleDeptMapper roleDeptMapper,
                            UserRoleMapper userRoleMapper,
                            PermissionMapper permissionMapper,
                            MenuMapper menuMapper,
                            DeptMapper deptMapper,
                            PermissionCacheService cacheService) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.deptMapper = deptMapper;
        this.cacheService = cacheService;
    }

    public PageResult<RoleDto.View> list(long page, long size, String keyword) {
        Page<RoleEntity> p = new Page<>(page, size);
        QueryWrapper<RoleEntity> w = new QueryWrapper<RoleEntity>().eq("mark", 1).orderByAsc("sort_order", "name");
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("name", keyword).or().like("description", keyword));
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
        // Unique name per tenant — see uk_core_rbac_role_name index in V18.
        assertNameUnique(req.name(), null);
        RoleEntity r = new RoleEntity();
        r.setId(IdGenerator.ulid());
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
        assertNotBuiltIn(r, "update");
        if (req.name() != null && !req.name().equals(r.getName())) {
            assertNameUnique(req.name(), id);
            r.setName(req.name());
        }
        if (req.description() != null) r.setDescription(req.description());
        if (req.dataScope() != null) r.setDataScope(req.dataScope());
        if (req.sortOrder() != null) r.setSortOrder(req.sortOrder());
        if (req.status() != null) r.setStatus(req.status());
        roleMapper.updateById(r);
        cacheService.evictRole(id);
    }

    @Transactional
    public void delete(String id, boolean force) {
        RoleEntity r = requireRole(id);
        assertNotBuiltIn(r, "delete");
        Long users = userRoleMapper.countActiveHoldersByRoleId(id);
        long userCount = users == null ? 0L : users;
        if (userCount > 0 && !force) {
            // IN_USE — caller may retry with force=true to clear user_role bindings.
            // detail map ships structured counts so the frontend can format an i18n message.
            throw new BusinessException(ErrorCode.IN_USE,
                    "Role is assigned to " + userCount + " user(s)",
                    java.util.Map.of("users", userCount));
        }
        // 注意：mark は @TableLogic フィールドのため、setMark(0)+updateById では SET 句に
        //   含まれず実際には削除されない（update_time / update_user だけ更新される）。
        //   明示的に UpdateWrapper で set("mark", 0) する必要がある。
        roleMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RoleEntity>()
                        .eq("id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
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
        // Evict caches BEFORE soft-deleting user_role — evictRole finds users via
        // the mark=1 user_role rows, so we need them still present at this point.
        cacheService.evictRole(id);
        userRoleMapper.update(null,
                new UpdateWrapper<UserRoleEntity>().eq("role_id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
    }

    public List<String> listPermissionIds(String roleId) {
        requireRole(roleId);
        // JOIN to permission.mark=1 — never return dangling links to soft-deleted
        // permissions, which would otherwise surface as "ghost selections" in the
        // admin UI and trip assertAllExist on save.
        return rolePermissionMapper.findActivePermissionIdsByRoleId(roleId);
    }

    @Transactional
    public void bindPermissions(String roleId, List<String> permissionIds) {
        assertNotBuiltIn(requireRole(roleId), "bind permissions");
        LinkedHashSet<String> dedup = dedupOrEmpty(permissionIds);
        assertAllExist(dedup, "permission", ids ->
                permissionMapper.selectCount(
                        new QueryWrapper<PermissionEntity>().eq("mark", 1).in("id", ids)));
        rolePermissionMapper.update(null,
                new UpdateWrapper<RolePermissionEntity>().eq("role_id", roleId).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        for (String permissionId : dedup) {
            RolePermissionEntity link = new RolePermissionEntity();
            link.setRoleId(roleId);
            link.setPermissionId(permissionId);
            rolePermissionMapper.insert(link);
        }
        cacheService.evictRole(roleId);
    }

    public List<String> listMenuIds(String roleId) {
        requireRole(roleId);
        // JOIN to menu.mark=1 — see listPermissionIds rationale.
        return roleMenuMapper.findActiveMenuIdsByRoleId(roleId);
    }

    @Transactional
    public void bindMenus(String roleId, List<String> menuIds) {
        assertNotBuiltIn(requireRole(roleId), "bind menus");
        LinkedHashSet<String> dedup = dedupOrEmpty(menuIds);
        assertAllExist(dedup, "menu", ids ->
                menuMapper.selectCount(
                        new QueryWrapper<MenuEntity>().eq("mark", 1).in("id", ids)));
        roleMenuMapper.update(null,
                new UpdateWrapper<RoleMenuEntity>().eq("role_id", roleId).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        for (String menuId : dedup) {
            RoleMenuEntity link = new RoleMenuEntity();
            link.setRoleId(roleId);
            link.setMenuId(menuId);
            roleMenuMapper.insert(link);
        }
        cacheService.evictRole(roleId);
    }

    public List<String> listDeptIds(String roleId) {
        requireRole(roleId);
        // JOIN to dept.mark=1 — see listPermissionIds rationale. The runtime
        // data-scope path keeps its own non-JOIN helper (findDeptIdsByRoleId).
        return roleDeptMapper.findActiveDeptIdsByRoleId(roleId);
    }

    @Transactional
    public void bindDepts(String roleId, List<String> deptIds) {
        assertNotBuiltIn(requireRole(roleId), "bind departments");
        LinkedHashSet<String> dedup = dedupOrEmpty(deptIds);
        assertAllExist(dedup, "department", ids ->
                deptMapper.selectCount(
                        new QueryWrapper<DeptEntity>().eq("mark", 1).in("id", ids)));
        roleDeptMapper.update(null,
                new UpdateWrapper<RoleDeptEntity>().eq("role_id", roleId).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        for (String deptId : dedup) {
            RoleDeptEntity link = new RoleDeptEntity();
            link.setRoleId(roleId);
            link.setDeptId(deptId);
            roleDeptMapper.insert(link);
        }
        cacheService.evictRole(roleId);
    }

    private static LinkedHashSet<String> dedupOrEmpty(List<String> ids) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (ids == null) return out;
        for (String id : ids) {
            if (id != null && !id.isBlank()) out.add(id);
        }
        return out;
    }

    /**
     * Verify every supplied id is the primary key of a live (mark=1) row in
     * the target table. Without this guard a typo or stale UI cache could
     * insert link rows pointing at non-existent ids; pre-FK this was silent
     * data corruption, post-FK (V9) the DB throws — but a friendly business
     * error from the service layer is cheaper to debug.
     */
    private static void assertAllExist(LinkedHashSet<String> ids, String kind,
                                       java.util.function.Function<LinkedHashSet<String>, Long> counter) {
        if (ids.isEmpty()) return;
        Long found = counter.apply(ids);
        if (found == null || found.intValue() != ids.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "One or more " + kind + " ids do not exist or are disabled");
        }
    }

    private RoleEntity requireRole(String id) {
        RoleEntity r = roleMapper.selectById(id);
        if (r == null || r.getMark() == null || r.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Role not found: " + id);
        }
        return r;
    }

    /**
     * Tenant-scoped name uniqueness check. Pass {@code excludeId=null} for create,
     * or the current role id for update (so a role's own name doesn't count as a dup).
     * Backed by index {@code uk_core_rbac_role_name} — surfacing as a business error
     * here keeps the 500 → 400 mapping clean instead of relying on the DB constraint.
     */
    private void assertNameUnique(String name, String excludeId) {
        QueryWrapper<RoleEntity> q = new QueryWrapper<RoleEntity>().eq("mark", 1).eq("name", name);
        if (excludeId != null) q.ne("id", excludeId);
        Long dup = roleMapper.selectCount(q);
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Role name already exists: " + name);
        }
    }

    /**
     * Built-in roles (is_built_in=1) are platform-seeded and referenced by application code
     * (e.g. {@code SUPER_ADMIN} is wired in {@code LocalAdminSeeder} and short-circuited in
     * {@code MenuQueryService}). They must stay read-only at the admin-API layer.
     */
    private void assertNotBuiltIn(RoleEntity r, String op) {
        if (Integer.valueOf(1).equals(r.getIsBuiltIn())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in role " + r.getName() + " is read-only (rejected: " + op + ")");
        }
    }

    private RoleDto.View toView(RoleEntity r) {
        return new RoleDto.View(
                r.getId(), r.getName(), r.getDescription(),
                r.getDataScope(), r.getIsBuiltIn(), r.getStatus(), r.getSortOrder());
    }
}
