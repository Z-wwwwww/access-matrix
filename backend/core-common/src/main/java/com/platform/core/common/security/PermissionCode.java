package com.platform.core.common.security;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;

/**
 * 権限コード常量工廠。
 *
 * <p>使い方（重要：Java 注解値はコンパイル時定数である必要があるため、
 * 「直接字面量」で宣言し、{@code static} ブロックで {@link #registerAll} を呼ぶ）：
 *
 * <pre>{@code
 *   @Component
 *   public final class SystemPermissions {
 *       public static final String USER_READ   = "user:read";
 *       public static final String USER_DELETE = "user:delete";
 *
 *       static { PermissionCode.registerAll(SystemPermissions.class, "system"); }
 *   }
 *
 *   // controller
 *   @RequiresPermission(SystemPermissions.USER_DELETE)
 * }</pre>
 *
 * <p>このパターンなら：
 * <ul>
 *   <li>定数は <em>コンパイル時定数</em> → {@code @RequiresPermission} の中で使える</li>
 *   <li>クラスロード時に静的ブロックが走り、{@link PermissionRegistry} に全部登録される</li>
 *   <li>{@code @Component} 化により Spring が起動時に強制ロード → Guard 検証より前に登録完了</li>
 * </ul>
 *
 * <p>通配 {@code *:*} 等は登録しない（割り当て可能な単位ではない）。
 * {@code @RequiresPermission("*:*")} と直接字面量で書く運用。
 */
public final class PermissionCode {

    /** 強制 code フォーマット：小文字 resource : 小文字 action（- と数字も可、ただし先頭は英字）。 */
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*");

    private PermissionCode() {}

    /**
     * 個別登録。常量クラスの static ブロック以外から直接呼ぶことは推奨しない
     * ({@link #registerAll} 経由で一括登録するのが標準パターン)。
     */
    public static String register(String code, String module) {
        if (code == null || !FORMAT.matcher(code).matches()) {
            throw new IllegalStateException(
                    "Invalid permission code format (expect resource:action, lowercase): " + code);
        }
        if (PermissionRegistry.isRegistered(code)) {
            throw new IllegalStateException("Duplicate permission code: " + code);
        }
        PermissionRegistry.put(code, module);
        return code;
    }

    /**
     * リフレクションで {@code cls} の全 {@code public static final String} 定数を読み、
     * 同じ {@code module} で一括登録する。
     *
     * <p>制限：{@code String} 型のみ。{@code String} 以外の定数は無視する。
     * 登録順は宣言順（{@link Class#getDeclaredFields()} の順序）。
     */
    public static void registerAll(Class<?> cls, String module) {
        for (Field f : cls.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!(Modifier.isStatic(m) && Modifier.isFinal(m) && Modifier.isPublic(m))) continue;
            if (f.getType() != String.class) continue;
            try {
                register((String) f.get(null), module);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to read permission constant: " + f.getName(), e);
            }
        }
    }
}
