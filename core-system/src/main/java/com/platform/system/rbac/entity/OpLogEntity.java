package com.platform.system.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Operation audit log row. Insert-only, no mark/audit fields — kept simple
 * because the row itself is the audit record.
 */
@Getter
@Setter
@TableName("core_oplog")
public class OpLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("user_id")
    private String userId;

    @TableField("username")
    private String username;

    @TableField("module")
    private String module;

    @TableField("action")
    private String action;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private String targetId;

    @TableField("request_uri")
    private String requestUri;

    @TableField("method")
    private String method;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("request_body")
    private String requestBody;

    @TableField("success")
    private Boolean success;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("cost_ms")
    private Integer costMs;

    @TableField("create_time")
    private LocalDateTime createTime;
}
