package com.platform.system.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("core_rbac_role_menu")
public class RoleMenuEntity extends BaseEntity {

    @TableField("role_id")
    private String roleId;

    @TableField("menu_id")
    private String menuId;
}
