package com.platform.system.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("core_rbac_role")
public class RoleEntity extends BaseEntity {

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    /** 1=ALL 2=DEPT_AND_SUB 3=DEPT 4=SELF 5=CUSTOM (used from Stage 3) */
    @TableField("data_scope")
    private Integer dataScope;

    /** 1 = built-in, cannot be deleted/renamed */
    @TableField("is_built_in")
    private Integer isBuiltIn;

    /** 1=enabled 0=disabled */
    @TableField("status")
    private Integer status;

    @TableField("sort_order")
    private Integer sortOrder;
}
