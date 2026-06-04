package com.platform.system.dict.builtin;

import com.platform.core.common.dict.DictEnum;

/**
 * Menu node type — built-in dictionary {@code menu_type}
 * ({@code core_rbac_menu.menu_type}: 1=directory 2=menu(page) 3=button).
 */
public enum MenuType implements DictEnum {

    DIRECTORY(1, "menu.option.type.directory"),
    MENU(2, "menu.option.type.menu"),
    BUTTON(3, "menu.option.type.button");

    private final int code;
    private final String labelKey;

    MenuType(int code, String labelKey) {
        this.code = code;
        this.labelKey = labelKey;
    }

    @Override public int code() { return code; }
    @Override public String labelKey() { return labelKey; }
}
