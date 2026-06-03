package com.platform.core.common.scheduling;

import java.time.LocalDateTime;

/**
 * 1 回の実行に渡される実行コンテキスト（純粋型 — Spring / DB に依存しない）。
 *
 * <p>定時タスクはすべてシステムレベルなので {@code tenantId} は常に {@code "system"}。
 *
 * @param jobCode     実行中のジョブコード
 * @param tenantId    実行コンテキストの租户（常に {@code "system"}）
 * @param triggerType 発火種別（cron / manual / startup）
 * @param fireTime    実行開始時刻
 */
public record JobContext(
        String jobCode,
        String tenantId,
        TriggerType triggerType,
        LocalDateTime fireTime) {
}
