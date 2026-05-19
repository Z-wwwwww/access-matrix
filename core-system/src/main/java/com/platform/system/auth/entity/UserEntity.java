package com.platform.system.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("core_auth_user")
public class UserEntity extends BaseEntity {

    @TableField("username")
    private String username;

    @TableField("email")
    private String email;

    @TableField("user_no")
    private String userNo;

    @TableField("display_name")
    private String displayName;

    @TableField("password_hash")
    private String passwordHash;

    /** JSON array stored in jsonb column, e.g. ["ADMIN","MANAGER"] */
    @TableField("roles")
    private String roles;

    /** JSON array stored in jsonb column, e.g. ["*:*"] */
    @TableField("authorities")
    private String authorities;

    /** 1=enabled, 0=locked */
    @TableField("status")
    private Integer status;
}
