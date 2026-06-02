package com.platform.system.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * In-app notification ("站内通知") — one row per recipient. The DB row is the
 * durable source of truth for the unread badge; SSE is only a best-effort
 * nudge to update it instantly.
 *
 * <p>Scoped by {@code recipient_user_id} (per-person inbox), NOT by department
 * data-scope. {@code tenant_id} (from {@link BaseEntity}) keeps tenants
 * isolated via the MyBatis-Plus tenant interceptor.
 */
@Getter
@Setter
@TableName("core_notification")
public class NotificationEntity extends BaseEntity {

    /** Business user id (ULID) this notification belongs to. */
    @TableField("recipient_user_id")
    private String recipientUserId;

    /** Machine type, e.g. {@code task.assigned}. */
    @TableField("type")
    private String type;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    /** Frontend route to open on click. Nullable. */
    @TableField("link")
    private String link;

    /** Optional source object type, e.g. {@code demo_task}. */
    @TableField("biz_type")
    private String bizType;

    @TableField("biz_id")
    private String bizId;

    /** 0=info(FYI) 1=action(needs the recipient to handle something). */
    @TableField("kind")
    private Integer kind;

    /** 1=info 2=warn 3=important. */
    @TableField("level")
    private Integer level;

    /** 0=unread 1=read. */
    @TableField("read_flag")
    private Integer readFlag;

    @TableField("read_time")
    private LocalDateTime readTime;
}
