package com.platform.system.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("core_rbac_user_role")
public class UserRoleEntity extends BaseEntity {

    @TableField("user_id")
    private String userId;

    @TableField("role_id")
    private String roleId;
}
