package com.platform.system.job.dto;

import java.time.LocalDateTime;

public final class JobLogDto {

    private JobLogDto() {}

    /** 実行ログの表示ビュー。 */
    public record View(
            String id,
            String jobCode,
            Integer triggerType,     // 1=cron 2=manual 3=startup
            Integer status,          // 1=running 2=success 3=fail 4=skipped
            String nodeId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long durationMs,
            String error,
            String triggeredBy) {}
}
