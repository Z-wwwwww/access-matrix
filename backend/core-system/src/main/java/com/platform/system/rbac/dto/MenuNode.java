package com.platform.system.rbac.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree node returned by {@code GET /api/menu/me}. Field names follow the
 * ele-admin-pro convention so the frontend's existing {@code formatMenus} /
 * {@code menuToRoutes} can consume the response untouched.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuNode {

    private String id;
    private String code;
    private String title;
    private Integer menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    /** 0=visible 1=hidden. Frontend reads this field directly. */
    private Integer hide;
    private Integer hideFooter;
    private Integer hideSidebar;
    /** 1 = pinned to the top of the sidebar (admin-controlled). */
    private Integer pinned;
    private String tabUnique;
    private String redirect;
    private String permissionCode;
    private List<MenuNode> children = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getMenuType() { return menuType; }
    public void setMenuType(Integer menuType) { this.menuType = menuType; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Integer getHide() { return hide; }
    public void setHide(Integer hide) { this.hide = hide; }

    public Integer getHideFooter() { return hideFooter; }
    public void setHideFooter(Integer hideFooter) { this.hideFooter = hideFooter; }

    public Integer getHideSidebar() { return hideSidebar; }
    public void setHideSidebar(Integer hideSidebar) { this.hideSidebar = hideSidebar; }

    public Integer getPinned() { return pinned; }
    public void setPinned(Integer pinned) { this.pinned = pinned; }

    public String getTabUnique() { return tabUnique; }
    public void setTabUnique(String tabUnique) { this.tabUnique = tabUnique; }

    public String getRedirect() { return redirect; }
    public void setRedirect(String redirect) { this.redirect = redirect; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public List<MenuNode> getChildren() { return children; }
    public void setChildren(List<MenuNode> children) { this.children = children; }
}
