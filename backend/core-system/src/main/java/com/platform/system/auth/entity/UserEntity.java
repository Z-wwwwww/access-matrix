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

    /**
     * Keycloak user UUID — the external identity anchor for users who log in
     * via OIDC. NULL for users still on the legacy password path. Filled by
     * {@code OidcJitUserService} on the first JWT seen for this identity.
     * See V21 migration for the index / nullability rationale.
     */
    @TableField("keycloak_id")
    private String keycloakId;

    /** 1=enabled, 0=locked */
    @TableField("status")
    private Integer status;

    /** Department the user belongs to — drives DEPT / DEPT_AND_SUB / SELF data scopes. */
    @TableField("dept_id")
    private String deptId;
}
