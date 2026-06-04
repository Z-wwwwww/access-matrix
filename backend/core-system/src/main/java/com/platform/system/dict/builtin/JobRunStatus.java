package com.platform.system.dict.builtin;

import com.platform.core.common.dict.DictEnum;

/**
 * Job execution status — built-in dictionary {@code job_run_status}
 * ({@code core_job_log.status}: 1=running 2=success 3=fail 4=skipped).
 *
 * <p>Note: {@code cssClass} here carries a Tailwind <b>text-color</b> class (the Job
 * page renders run-status as colored text, not a Badge variant). Consuming pages
 * decide how to apply {@code cssClass}.
 */
public enum JobRunStatus implements DictEnum {

    RUNNING(1, "job.runStatus.running", "text-amber-600"),
    SUCCESS(2, "job.runStatus.success", "text-emerald-600"),
    FAIL(3, "job.runStatus.fail", "text-destructive"),
    SKIPPED(4, "job.runStatus.skipped", "text-muted-foreground");

    private final int code;
    private final String labelKey;
    private final String cssClass;

    JobRunStatus(int code, String labelKey, String cssClass) {
        this.code = code;
        this.labelKey = labelKey;
        this.cssClass = cssClass;
    }

    @Override public int code() { return code; }
    @Override public String labelKey() { return labelKey; }
    @Override public String cssClass() { return cssClass; }
}
