package com.platform.business.demo.task;

import com.platform.core.common.dict.DictEnum;

/**
 * Task lifecycle status — built-in dictionary {@code task_status}. The stored
 * {@code demo_task.status} column holds {@link #code()}; this enum is its single
 * source of truth (replaces the bare {@code 1=TODO 2=DOING ...} magic numbers
 * that previously lived only in a SQL comment + per-page frontend maps).
 */
public enum TaskStatus implements DictEnum {

    TODO(1, "task.status.todo", "outline"),
    DOING(2, "task.status.doing", "default"),
    DONE(3, "task.status.done", "default"),
    CANCELLED(4, "task.status.cancelled", "destructive");

    private final int code;
    private final String labelKey;
    private final String cssClass;

    TaskStatus(int code, String labelKey, String cssClass) {
        this.code = code;
        this.labelKey = labelKey;
        this.cssClass = cssClass;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String labelKey() {
        return labelKey;
    }

    @Override
    public String cssClass() {
        return cssClass;
    }
}
