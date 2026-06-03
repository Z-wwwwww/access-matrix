package com.platform.core.common.scheduling;

/**
 * 1 回の実行がどのきっかけで起きたか。{@code core_job_log.trigger_type} に対応。
 */
public enum TriggerType {
    /** cron スケジュールによる自動発火（DB 値 1）。 */
    CRON(1),
    /** 管理画面の「即時実行」ボタン（DB 値 2）。 */
    MANUAL(2),
    /** 起動同期時の初回実行（現状未使用、将来の予約。DB 値 3）。 */
    STARTUP(3);

    private final int code;

    TriggerType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
