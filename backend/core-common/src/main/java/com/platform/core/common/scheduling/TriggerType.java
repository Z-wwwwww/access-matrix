package com.platform.core.common.scheduling;

import com.platform.core.common.dict.DictEnum;

/**
 * 1 回の実行がどのきっかけで起きたか。{@code core_job_log.trigger_type} に対応。
 * Also the built-in dictionary {@code job_trigger_type} (single source of truth
 * for both scheduling logic and the frontend dropdown/label).
 */
public enum TriggerType implements DictEnum {
    /** cron スケジュールによる自動発火（DB 値 1）。 */
    CRON(1, "job.triggerType.cron"),
    /** 管理画面の「即時実行」ボタン（DB 値 2）。 */
    MANUAL(2, "job.triggerType.manual"),
    /** 起動同期時の初回実行（現状未使用、将来の予約。DB 値 3）。 */
    STARTUP(3, "job.triggerType.startup");

    private final int code;
    private final String labelKey;

    TriggerType(int code, String labelKey) {
        this.code = code;
        this.labelKey = labelKey;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String labelKey() {
        return labelKey;
    }
}
