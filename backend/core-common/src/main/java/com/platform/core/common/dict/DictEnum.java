package com.platform.core.common.dict;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;

/**
 * Marks an enum as a <b>built-in dictionary</b> — a fixed, code-defined set of
 * options that application logic branches on (status / state / type). These are
 * the single source of truth for their values: the DB stores the raw {@link #code()}
 * (e.g. {@code task.status = 2}) and code switches on it, so the set MUST NOT be
 * runtime-editable. Register the enum into {@link DictRegistry} at startup; the
 * {@code GET /dict/{code}} endpoint then exposes it read-only for frontend dropdowns.
 *
 * <p>Contrast with <b>managed dictionaries</b> (the {@code core_dict} /
 * {@code core_dict_item} tables) which are runtime-editable business lookups that
 * code does NOT branch on.
 */
public interface DictEnum {

    /** The stored numeric value (what lands in the business column, e.g. {@code status=2}). */
    int code();

    /** Frontend i18n key for the label, e.g. {@code "task.status.todo"}. Resolved client-side via {@code t()}. */
    String labelKey();

    /** Optional Badge variant for display (e.g. {@code "outline"} / {@code "destructive"}); null = default. */
    default String cssClass() {
        return null;
    }

    /**
     * Reverse lookup: stored code → enum constant (for {@code switch} / comparison).
     * Returns null if no constant matches.
     *
     * <pre>{@code
     *   TaskStatus s = DictEnum.fromCode(TaskStatus.class, task.getStatus());
     *   if (s == TaskStatus.DONE) { ... }
     * }</pre>
     */
    static <E extends Enum<E> & DictEnum> E fromCode(Class<E> type, Integer code) {
        if (code == null) return null;
        for (E e : type.getEnumConstants()) {
            if (e.code() == code) return e;
        }
        return null;
    }

    /**
     * Validate that {@code code} is a legal value of {@code type}; throw
     * {@link BusinessException} (→ HTTP 400) otherwise. Use at the service boundary
     * so a client cannot persist an out-of-range status/type (e.g. {@code dataScope=99}).
     */
    static <E extends Enum<E> & DictEnum> void requireValid(Class<E> type, Integer code, String field) {
        if (fromCode(type, code) == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Invalid " + field + ": " + code);
        }
    }
}
