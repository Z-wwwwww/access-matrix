package com.platform.system.dict.builtin;

import com.platform.core.common.dict.DictEnum;

/**
 * Data-scope presets — built-in dictionary {@code data_scope}
 * (role data_scope: 1=ALL 2=DEPT_AND_SUB 3=DEPT 4=SELF 5=CUSTOM). See @DataScope.
 */
public enum DataScopeDict implements DictEnum {

    ALL(1, "role.option.scope.all"),
    DEPT_AND_SUB(2, "role.option.scope.deptAndSub"),
    DEPT(3, "role.option.scope.dept"),
    SELF(4, "role.option.scope.self"),
    CUSTOM(5, "role.option.scope.custom");

    private final int code;
    private final String labelKey;

    DataScopeDict(int code, String labelKey) {
        this.code = code;
        this.labelKey = labelKey;
    }

    @Override public int code() { return code; }
    @Override public String labelKey() { return labelKey; }
}
