package com.platform.system.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("core_rbac_permission")
public class PermissionEntity extends BaseEntity {

    /** Permission string, format: resource:action (e.g. user:read, *:*) */
    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("resource")
    private String resource;

    @TableField("action")
    private String action;

    /** Owning module: system / pms / iot / ... */
    @TableField("module")
    private String module;

    @TableField("description")
    private String description;

    @TableField("is_built_in")
    private Integer isBuiltIn;
}
