package com.platform.core.common.dict;

/**
 * Generic enabled/disabled status — built-in dictionary {@code common_status}
 * (1=enabled, 0=disabled). Lives in core-common (like {@code TriggerType}) because
 * it is cross-cutting: user / dept / role and any business module's on/off column
 * can reuse it without depending on core-system.
 */
public enum CommonStatus implements DictEnum {

    ENABLED(1, "common.status.active", "default"),
    DISABLED(0, "common.status.inactive", "outline");

    private final int code;
    private final String labelKey;
    private final String cssClass;

    CommonStatus(int code, String labelKey, String cssClass) {
        this.code = code;
        this.labelKey = labelKey;
        this.cssClass = cssClass;
    }

    @Override public int code() { return code; }
    @Override public String labelKey() { return labelKey; }
    @Override public String cssClass() { return cssClass; }
}
