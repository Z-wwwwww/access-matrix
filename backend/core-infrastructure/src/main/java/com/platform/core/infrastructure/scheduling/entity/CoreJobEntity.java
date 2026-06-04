package com.platform.core.infrastructure.scheduling.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 定時任務の設定行。{@code core_job} (V37)。動的スケジューラの「可変設定」側で、
 * ジョブのロジック自体はコード ({@code ScheduledJob} bean) にある。
 *
 * <p>PLATFORM ジョブは {@code tenant_id='system'} の 1 行、TENANT ジョブは租户ごとに
 * 1 行。{@link BaseEntity} が id / tenant_id / mark / 監査列を自動補填する。
 *
 * <p>注意：last_* 列の更新や cron/enabled の更新はマルチノード/並行から起こりうるので、
 * 楽観ロック ({@code @Version} = update_time) との衝突を避けるため、サービス側では
 * {@code updateById} ではなく {@code UpdateWrapper} で明示更新する。
 */
@Getter
@Setter
@TableName("core_job")
public class CoreJobEntity extends BaseEntity {

    @TableField("job_code")
    private String jobCode;

    @TableField("name")
    private String name;

    @TableField("cron")
    private String cron;

    /** 1=有効(スケジュール中) 0=停止 */
    @TableField("enabled")
    private Integer enabled;

    /** 1=重複実行可 0=実行中はスキップ */
    @TableField("concurrent")
    private Integer concurrent;

    @TableField("max_run_seconds")
    private Integer maxRunSeconds;

    @TableField("last_fire_time")
    private LocalDateTime lastFireTime;

    /** 直近実行の結果。core_job_log.status に同じ（2=成功 3=失敗）。 */
    @TableField("last_status")
    private Integer lastStatus;

    @TableField("last_duration_ms")
    private Long lastDurationMs;

    @TableField("remark")
    private String remark;
}
