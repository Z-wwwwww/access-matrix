package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.rbac.dto.MenuAdminDto;
import com.platform.system.rbac.entity.MenuEntity;
import com.platform.system.rbac.entity.RoleMenuEntity;
import com.platform.system.rbac.mapper.MenuMapper;
import com.platform.system.rbac.mapper.RoleMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuAdminService {

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final PermissionCacheService cacheService;

    public MenuAdminService(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper, PermissionCacheService cacheService) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.cacheService = cacheService;
    }

    public List<MenuAdminDto.View> listAll() {
        return menuMapper.selectList(new QueryWrapper<MenuEntity>()
                .eq("mark", 1)
                .orderByAsc("parent_id", "sort_order", "code"))
                .stream().map(this::toView).toList();
    }

    public MenuAdminDto.View get(String id) {
        return toView(require(id));
    }

    @Transactional
    public String create(MenuAdminDto.CreateRequest req) {
        Long dup = menuMapper.selectCount(new QueryWrapper<MenuEntity>().eq("mark", 1).eq("code", req.code()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Menu code already exists: " + req.code());
        }
        MenuEntity m = new MenuEntity();
        m.setId(IdGenerator.ulid());
        m.setParentId(req.parentId());
        m.setCode(req.code());
        m.setTitle(req.title());
        m.setMenuType(req.menuType());
        m.setPath(req.path());
        m.setComponent(req.component());
        m.setIcon(req.icon());
        m.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        m.setHide(req.hide() == null ? 0 : req.hide());
        m.setHideFooter(req.hideFooter() == null ? 0 : req.hideFooter());
        m.setHideSidebar(req.hideSidebar() == null ? 0 : req.hideSidebar());
        m.setTabUnique(req.tabUnique());
        m.setRedirect(req.redirect());
        m.setPermissionCode(req.permissionCode());
        m.setStatus(req.status() == null ? 1 : req.status());
        menuMapper.insert(m);
        cacheService.evictAllMenus();
        return m.getId();
    }

    @Transactional
    public void update(String id, MenuAdminDto.UpdateRequest req) {
        MenuEntity m = require(id);
        if (req.parentId() != null) m.setParentId(req.parentId());
        if (req.title() != null) m.setTitle(req.title());
        if (req.menuType() != null) m.setMenuType(req.menuType());
        if (req.path() != null) m.setPath(req.path());
        if (req.component() != null) m.setComponent(req.component());
        if (req.icon() != null) m.setIcon(req.icon());
        if (req.sortOrder() != null) m.setSortOrder(req.sortOrder());
        if (req.hide() != null) m.setHide(req.hide());
        if (req.hideFooter() != null) m.setHideFooter(req.hideFooter());
        if (req.hideSidebar() != null) m.setHideSidebar(req.hideSidebar());
        if (req.tabUnique() != null) m.setTabUnique(req.tabUnique());
        if (req.redirect() != null) m.setRedirect(req.redirect());
        if (req.permissionCode() != null) m.setPermissionCode(req.permissionCode());
        if (req.status() != null) m.setStatus(req.status());
        menuMapper.updateById(m);
        cacheService.evictAllMenus();
    }

    @Transactional
    public void delete(String id) {
        MenuEntity m = require(id);
        // Refuse delete when children exist.
        Long children = menuMapper.selectCount(new QueryWrapper<MenuEntity>().eq("mark", 1).eq("parent_id", id));
        if (children != null && children > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Menu has children; remove them first");
        }
        m.setMark(0);
        menuMapper.updateById(m);
        roleMenuMapper.update(null,
                new UpdateWrapper<RoleMenuEntity>().eq("menu_id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
        cacheService.evictAllMenus();
    }

    private MenuEntity require(String id) {
        MenuEntity m = menuMapper.selectById(id);
        if (m == null || m.getMark() == null || m.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Menu not found: " + id);
        }
        return m;
    }

    private MenuAdminDto.View toView(MenuEntity m) {
        return new MenuAdminDto.View(
                m.getId(), m.getParentId(), m.getCode(), m.getTitle(),
                m.getMenuType(), m.getPath(), m.getComponent(), m.getIcon(),
                m.getSortOrder(), m.getHide(), m.getHideFooter(), m.getHideSidebar(),
                m.getTabUnique(), m.getRedirect(), m.getPermissionCode(), m.getStatus());
    }
}
