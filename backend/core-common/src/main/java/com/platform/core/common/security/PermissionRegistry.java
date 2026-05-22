package com.platform.core.common.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 全局只读权限码注册表。
 *
 * <p>所有通过 {@link PermissionCode#register(String, String)} 声明的权限会被记录在这里。
 * {@code PermissionConsistencyGuard}（在 core-system）启动期对照本注册表 +
 * 实际注解 + DB 表三方做强一致性校验。
 *
 * <p>包级方法 {@link #put(String, String)} 仅供 {@link PermissionCode} 调用，
 * 业务代码只能查询。
 */
public final class PermissionRegistry {

    private static final Map<String, Entry> CODES = new LinkedHashMap<>();

    private PermissionRegistry() {}

    /** 已注册的权限码（按声明顺序）。 */
    public static Set<String> allCodes() {
        return Collections.unmodifiableSet(CODES.keySet());
    }

    /** 所有注册项（按声明顺序）。 */
    public static Map<String, Entry> allEntries() {
        return Collections.unmodifiableMap(CODES);
    }

    public static Entry get(String code) {
        return CODES.get(code);
    }

    public static boolean isRegistered(String code) {
        return CODES.containsKey(code);
    }

    /**
     * 通配规则 {@code *:*} / {@code resource:*} —— 不入注册表（不是可分配单元），
     * 但允许出现在 {@code @RequiresPermission} 字面量里，Guard 校验时要白名单它。
     */
    public static boolean isWildcard(String code) {
        return code != null && code.contains("*");
    }

    static void put(String code, String module) {
        int colon = code.indexOf(':');
        String resource = code.substring(0, colon);
        String action = code.substring(colon + 1);
        CODES.put(code, new Entry(code, resource, action, module));
    }

    /** 不可变的注册项。{@code module} 可能为 null。 */
    public record Entry(String code, String resource, String action, String module) {}
}
