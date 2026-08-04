package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.dict.CommonStatus;
import com.platform.core.common.dict.DictEnum;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ConcurrentEdit;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.dict.builtin.MenuType;
import com.platform.system.rbac.dto.MenuAdminDto;
import com.platform.system.rbac.entity.MenuEntity;
import com.platform.system.rbac.entity.RoleMenuEntity;
import com.platform.system.rbac.mapper.MenuMapper;
import com.platform.system.rbac.mapper.RoleMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Service
public class MenuAdminService {

    private static final TypeReference<Map<String, String>> I18N_MAP = new TypeReference<>() {};

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final PermissionCacheService cacheService;
    private final JsonMapper jsonMapper;

    public MenuAdminService(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper,
                            PermissionCacheService cacheService, JsonMapper jsonMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.cacheService = cacheService;
        this.jsonMapper = jsonMapper;
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
        // A brand-new id can't be its own ancestor, so only the existence check
        // applies here (a dangling parentId would silently promote the node to a
        // root in MenuQueryService.assembleTree).
        if (req.parentId() != null && !req.parentId().isBlank()) {
            require(req.parentId());
        }
        m.setParentId(req.parentId());
        m.setCode(req.code());
        m.setTitle(req.title());
        m.setTitleI18n(serializeI18n(req.titleI18n()));
        DictEnum.requireValid(MenuType.class, req.menuType(), "menuType");
        m.setMenuType(req.menuType());
        m.setPath(req.path());
        m.setComponent(req.component());
        m.setIcon(req.icon());
        m.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        m.setHide(req.hide() == null ? 0 : req.hide());
        m.setHideFooter(req.hideFooter() == null ? 0 : req.hideFooter());
        m.setHideSidebar(req.hideSidebar() == null ? 0 : req.hideSidebar());
        m.setPinned(req.pinned() == null ? 0 : req.pinned());
        m.setTabUnique(req.tabUnique());
        m.setRedirect(req.redirect());
        m.setPermissionCode(req.permissionCode());
        Integer status = req.status() == null ? CommonStatus.ENABLED.code() : req.status();
        DictEnum.requireValid(CommonStatus.class, status, "status");
        m.setStatus(status);
        menuMapper.insert(m);
        cacheService.evictAllMenus();
        return m.getId();
    }

    @Transactional
    public void update(String id, MenuAdminDto.UpdateRequest req) {
        MenuEntity m = require(id);
        if (req.parentId() != null) {
            assertReparentable(id, req.parentId());
            m.setParentId(req.parentId());
        }
        if (req.title() != null) m.setTitle(req.title());
        if (req.titleI18n() != null) m.setTitleI18n(serializeI18n(req.titleI18n()));
        if (req.menuType() != null) {
            DictEnum.requireValid(MenuType.class, req.menuType(), "menuType");
            m.setMenuType(req.menuType());
        }
        if (req.path() != null) m.setPath(req.path());
        if (req.component() != null) m.setComponent(req.component());
        if (req.icon() != null) m.setIcon(req.icon());
        if (req.sortOrder() != null) m.setSortOrder(req.sortOrder());
        if (req.hide() != null) m.setHide(req.hide());
        if (req.hideFooter() != null) m.setHideFooter(req.hideFooter());
        if (req.hideSidebar() != null) m.setHideSidebar(req.hideSidebar());
        if (req.pinned() != null) m.setPinned(req.pinned());
        if (req.tabUnique() != null) m.setTabUnique(req.tabUnique());
        if (req.redirect() != null) m.setRedirect(req.redirect());
        if (req.permissionCode() != null) m.setPermissionCode(req.permissionCode());
        if (req.status() != null) {
            DictEnum.requireValid(CommonStatus.class, req.status(), "status");
            m.setStatus(req.status());
        }
        ConcurrentEdit.requireApplied(menuMapper.updateById(m));
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
        // mark は @TableLogic — BaseMapper.updateById では SET 句から除外されるので UpdateWrapper で明示。
        menuMapper.update(null,
                new UpdateWrapper<MenuEntity>().eq("id", id).eq("mark", 1)
                        .set("mark", 0).set("update_user", "system"));
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

    /**
     * Reject a re-parent that would make {@code id} its own ancestor. The frontend's
     * parent picker already excludes self + descendants, but the endpoint is the real
     * boundary and a cycle here is expensive: {@code MenuQueryService.assembleTree}
     * attaches every node to its parent and only collects parent-less nodes as roots,
     * so a node inside a cycle is reachable from no root — the whole branch silently
     * disappears from {@code /menu/me} for EVERY user (menus are a single global set
     * since V41), with nothing in the response to say why. Mirrors the equivalent
     * guards in {@code DeptAdminService.update}.
     *
     * <p>Blank parentId means "make it a root" — always allowed.
     */
    private void assertReparentable(String id, String newParentId) {
        if (newParentId.isBlank()) return;
        if (newParentId.equals(id)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "A menu cannot be its own parent");
        }
        // Walk up from the proposed parent. Hitting `id` means the proposed parent is
        // one of our own descendants → cycle. The visited set keeps the walk finite
        // even if the table already contains a cycle from before this guard existed.
        java.util.Set<String> seen = new java.util.HashSet<>();
        String cursor = newParentId;
        while (cursor != null && !cursor.isBlank() && seen.add(cursor)) {
            if (cursor.equals(id)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "New parent is a descendant — would create a cycle");
            }
            MenuEntity ancestor = menuMapper.selectById(cursor);
            if (ancestor == null || ancestor.getMark() == null || ancestor.getMark() != 1) {
                // The chain leaves the live tree. If it broke on the very first hop the
                // caller pointed at a non-existent parent; deeper up it's pre-existing
                // dirty data that this edit doesn't make worse.
                if (cursor.equals(newParentId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "Menu not found: " + newParentId);
                }
                return;
            }
            cursor = ancestor.getParentId();
        }
    }

    private MenuAdminDto.View toView(MenuEntity m) {
        return new MenuAdminDto.View(
                m.getId(), m.getParentId(), m.getCode(), m.getTitle(),
                parseI18n(m.getTitleI18n()),
                m.getMenuType(), m.getPath(), m.getComponent(), m.getIcon(),
                m.getSortOrder(), m.getHide(), m.getHideFooter(), m.getHideSidebar(),
                m.getPinned(), m.getTabUnique(), m.getRedirect(), m.getPermissionCode(), m.getStatus());
    }

    /**
     * Parse the raw {@code title_i18n} JSON string into a Map for DTO output.
     * Returns null (not empty map) when the column is null/blank so the wire payload
     * keeps it absent ({@code @JsonInclude(NON_NULL)} on the consuming DTO).
     * Malformed JSON falls back to null with a warning rather than failing the request.
     */
    private Map<String, String> parseI18n(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return jsonMapper.readValue(raw, I18N_MAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Serialise a translations map to JSON for the {@code title_i18n} column.
     * Null/empty map → null (we want NULL in the column, not "{}"), so the
     * fallback ladder in {@code useMenuTitle} stays clean.
     */
    private String serializeI18n(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return jsonMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Invalid titleI18n payload");
        }
    }
}
