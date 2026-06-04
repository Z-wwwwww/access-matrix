package com.platform.system.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class JobDto {

    private JobDto() {}

    /**
     * 設定編集リクエスト。cron は必須、その他は省略可（null なら据え置き）。
     * job_code は変更不可（コード側が真実の源）。
     */
    public record UpdateRequest(
            @NotBlank @Size(max = 128) String cron,
            @Min(1) @Max(86400) Integer maxRunSeconds,
            @Min(0) @Max(1) Integer concurrent,
            @Size(max = 512) String remark) {}

    /**
     * 一覧/詳細の表示ビュー。{@code nextFireTime} は cron から実時計算（DB 非保存）。
     */
    public record View(
            String id,
            String tenantId,
            String jobCode,
            String name,
            String cron,
            Integer enabled,         // 1=有効 0=停止
            Integer concurrent,
            Integer maxRunSeconds,
            LocalDateTime lastFireTime,
            Integer lastStatus,      // 2=成功 3=失敗
            Long lastDurationMs,
            LocalDateTime nextFireTime,
            String remark,
            LocalDateTime createTime,
            LocalDateTime updateTime) {}
}
