package com.platform.core.infrastructure.scheduling.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 1 回の実行の履歴行。{@code core_job_log} (V38)。RUNNING で挿入し、終了時に
 * SUCCESS/FAIL へ確定する。挿入は {@link BaseEntity} の自動補填で行の tenant_id が
 * 実行時の {@code RequestContext} 租户になる（PLATFORM ジョブは 'system'）。
 */
@Getter
@Setter
@TableName("core_job_log")
public class CoreJobLogEntity extends BaseEntity {

    @TableField("job_code")
    private String jobCode;

    /** 1=cron 2=manual 3=startup */
    @TableField("trigger_type")
    private Integer triggerType;

    /** 1=running 2=success 3=fail 4=skipped */
    @TableField("status")
    private Integer status;

    @TableField("node_id")
    private String nodeId;

    @TableField("start_time")
    private OffsetDateTime startTime;

    @TableField("end_time")
    private OffsetDateTime endTime;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error")
    private String error;

    @TableField("triggered_by")
    private String triggeredBy;
}
