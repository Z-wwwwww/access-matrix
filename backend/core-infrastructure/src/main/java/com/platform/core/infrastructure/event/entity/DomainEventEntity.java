package com.platform.core.infrastructure.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row in {@code core_domain_event} (V36). Insert-mostly: business code
 * only ever inserts; the {@code dispatch*} columns are mutated solely by the
 * outbox dispatcher. Deliberately does NOT extend {@code BaseEntity} — an
 * event has no {@code mark} (events are never soft-deleted; they age out via
 * a retention job) and no {@code update_user}.
 */
@Getter
@Setter
@TableName("core_domain_event")
public class DomainEventEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("aggregate_type")
    private String aggregateType;

    @TableField("aggregate_id")
    private String aggregateId;

    @TableField("event_type")
    private String eventType;

    /** Structured fact, stored as JSONB. Serialized from the event payload via the Jackson3 JsonMapper. */
    @TableField("payload")
    private String payload;

    @TableField("actor")
    private String actor;

    /** {@link com.platform.core.infrastructure.event.ActorType} code: 1 human / 2 ai / 3 system. */
    @TableField("actor_type")
    private Integer actorType;

    @TableField("trace_id")
    private String traceId;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    /** Outbox state: 0 pending / 1 dispatched / 2 failed. Owned by the dispatcher. */
    @TableField("dispatch_state")
    private Integer dispatchState;

    @TableField("dispatch_attempts")
    private Integer dispatchAttempts;

    @TableField("dispatched_at")
    private LocalDateTime dispatchedAt;
}
