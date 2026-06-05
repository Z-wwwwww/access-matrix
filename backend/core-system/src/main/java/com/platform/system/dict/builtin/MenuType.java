package com.platform.system.dict.builtin;

import com.platform.core.common.dict.DictEnum;

/**
 * Menu node type — built-in dictionary {@code menu_type}
 * ({@code core_rbac_menu.menu_type}: 1=directory 2=menu(page) 3=button).
 */
public enum MenuType implements DictEnum {

    // Distinct Badge variants per type so the menu list reads at a glance:
    // directory = info (blue), menu/page = success (green), button = violet.
    DIRECTORY(1, "menu.option.type.directory", "info"),
    MENU(2, "menu.option.type.menu", "success"),
    BUTTON(3, "menu.option.type.button", "violet");

    private final int code;
    private final String labelKey;
    private final String cssClass;

    MenuType(int code, String labelKey, String cssClass) {
        this.code = code;
        this.labelKey = labelKey;
        this.cssClass = cssClass;
    }

    @Override public int code() { return code; }
    @Override public String labelKey() { return labelKey; }
    @Override public String cssClass() { return cssClass; }
}
