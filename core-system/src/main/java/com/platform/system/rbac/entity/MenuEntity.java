package com.platform.system.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("core_rbac_menu")
public class MenuEntity extends BaseEntity {

    @TableField("parent_id")
    private String parentId;

    /** Internal management code, e.g. {@code "system.user"}. */
    @TableField("code")
    private String code;

    /** Display title used by the sidebar / tab bar. */
    @TableField("title")
    private String title;

    /** 1=directory 2=menu(page) 3=button */
    @TableField("menu_type")
    private Integer menuType;

    /** vue-router path, e.g. {@code "/system/user"}. */
    @TableField("path")
    private String path;

    /** Relative .vue path or URL, e.g. {@code "/system/user/index"}. */
    @TableField("component")
    private String component;

    @TableField("icon")
    private String icon;

    @TableField("sort_order")
    private Integer sortOrder;

    /** 1 = hidden from sidebar but still routable. */
    @TableField("hide")
    private Integer hide;

    @TableField("hide_footer")
    private Integer hideFooter;

    @TableField("hide_sidebar")
    private Integer hideSidebar;

    @TableField("tab_unique")
    private String tabUnique;

    @TableField("redirect")
    private String redirect;

    /** Optional permission required to view this entry (resource:action). */
    @TableField("permission_code")
    private String permissionCode;

    /** 1=enabled, 0=disabled. */
    @TableField("status")
    private Integer status;
}
