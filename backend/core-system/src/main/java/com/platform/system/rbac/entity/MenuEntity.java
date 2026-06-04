package com.platform.system.rbac.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Navigation menu row. A single GLOBAL set (V41/V43) — NOT tenant-scoped — so,
 * unlike most entities, it does <b>not</b> extend {@code BaseEntity} (which
 * carries {@code tenant_id}). {@code core_rbac_menu} has no {@code tenant_id}
 * column and is listed in {@code MybatisPlusConfig.TENANT_EXCLUDED_TABLES};
 * per-user visibility is decided by {@code permission_code} (see
 * {@code MenuQueryService}), not by tenant. The soft-delete ({@code mark}) +
 * audit columns BaseEntity would provide are declared here directly.
 */
@Getter
@Setter
@TableName("core_rbac_menu")
public class MenuEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableLogic(value = "1", delval = "0")
    @TableField(value = "mark", fill = FieldFill.INSERT)
    private Integer mark;

    @TableField(value = "create_user", fill = FieldFill.INSERT)
    private String createUser;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_user", fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    @Version
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ── business fields ──────────────────────────────────────────────

    @TableField("parent_id")
    private String parentId;

    /** Internal management code, e.g. {@code "system.user"}. */
    @TableField("code")
    private String code;

    /** Display title used by the sidebar / tab bar. Fallback only — prefer {@link #titleI18n}. */
    @TableField("title")
    private String title;

    /**
     * Locale → translated title, stored as raw JSON in the {@code title_i18n} jsonb column.
     * Service layer (de)serialises via Jackson. Sent over the wire to the frontend so
     * {@code useMenuTitle()} can pick the right locale at render time.
     */
    @TableField("title_i18n")
    private String titleI18n;

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

    /** 1 = pin this menu to the top of the sidebar (admin-controlled). */
    @TableField("pinned")
    private Integer pinned;

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
