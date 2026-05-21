package com.platform.system.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("core_rbac_dept")
public class DeptEntity extends BaseEntity {

    @TableField("parent_id")
    private String parentId;

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    /** Materialised path "/rootId/.../selfId" — leading slash, no trailing slash. */
    @TableField("path")
    private String path;

    @TableField("level")
    private Integer level;

    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * Informational pointer to the user who heads this department. Pure memo:
     * {@code DataScopeQueryService} and the permission stack never read it,
     * so flipping this value cannot grant or revoke any access.
     * Existence is validated by {@code DeptAdminService} at write time.
     */
    @TableField("leader_user_id")
    private String leaderUserId;

    /** 1=enabled 0=disabled */
    @TableField("status")
    private Integer status;
}
