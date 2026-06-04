package com.platform.system.dict.builtin;

import com.platform.core.common.dict.DictEnum;

/**
 * Tenant status — built-in dictionary {@code tenant_status} (1=active, 0=suspended).
 * Distinct from {@link CommonStatus} because the labels differ (suspended ≠ disabled).
 */
public enum TenantStatus implements DictEnum {

    ACTIVE(1, "platform.tenant.status.active", "default"),
    SUSPENDED(0, "platform.tenant.status.suspended", "outline");

    private final int code;
    private final String labelKey;
    private final String cssClass;

    TenantStatus(int code, String labelKey, String cssClass) {
        this.code = code;
        this.labelKey = labelKey;
        this.cssClass = cssClass;
    }

    @Override public int code() { return code; }
    @Override public String labelKey() { return labelKey; }
    @Override public String cssClass() { return cssClass; }
}
