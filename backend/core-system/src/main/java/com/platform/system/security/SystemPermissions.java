package com.platform.system.security;

import com.platform.core.common.security.PermissionCode;
import org.springframework.stereotype.Component;

/**
 * core-system モジュールの権限コード常量。
 *
 * <p>定数は <em>必ず直接字面量</em>で宣言する（Java の注解値はコンパイル時定数を要求するため、
 * メソッド戻り値で初期化すると {@code @RequiresPermission(SystemPermissions.X)} と書けなくなる）。
 * 登録は {@code static} ブロックでリフレクション一括 ({@link PermissionCode#registerAll})。
 *
 * <p>{@code @Component} は Spring に強制ロードさせるため。クラスロード → 静的ブロック実行 →
 * {@code PermissionRegistry} に全コード登録、という順序が {@code PermissionConsistencyGuard}
 * の検証前に確実に完了する。
 *
 * <p>新しい権限を追加するときの手順：
 * <ol>
 *   <li>本クラスに {@code public static final String XXX = "module:action";} を追加</li>
 *   <li>controller に {@code @RequiresPermission(SystemPermissions.XXX)} を付ける</li>
 *   <li>アプリ起動 → Guard が DB と i18n を自動で揃える</li>
 *   <li>5 言語の翻訳を埋めて {@code __TODO__} を消す（CI が grep で阻止する）</li>
 * </ol>
 */
@Component
public final class SystemPermissions {

    // ---- user ----
    public static final String USER_READ   = "user:read";
    public static final String USER_CREATE = "user:create";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_DELETE = "user:delete";

    // ---- role ----
    public static final String ROLE_READ   = "role:read";
    public static final String ROLE_CREATE = "role:create";
    public static final String ROLE_UPDATE = "role:update";
    public static final String ROLE_DELETE = "role:delete";

    // ---- menu ----
    public static final String MENU_READ   = "menu:read";
    public static final String MENU_CREATE = "menu:create";
    public static final String MENU_UPDATE = "menu:update";
    public static final String MENU_DELETE = "menu:delete";

    // ---- dept ----
    public static final String DEPT_READ   = "dept:read";
    public static final String DEPT_CREATE = "dept:create";
    public static final String DEPT_UPDATE = "dept:update";
    public static final String DEPT_DELETE = "dept:delete";

    // ---- permission 字典（読みだけ。CRUD はもう存在しない） ----
    public static final String PERMISSION_READ = "permission:read";

    // ---- oplog ----
    public static final String OPLOG_READ = "oplog:read";

    // ---- auth ----
    public static final String AUTH_UNLOCK         = "auth:unlock";
    public static final String AUTH_RESET_PASSWORD = "auth:reset-password";

    // platform: namespace は {@link PlatformPermissions} に分離。
    // module="platform" として登録する必要があるため別クラスに切り出している。
    // 同じクラス内に置くと registerAll の module 引数が一つしか取れず
    // V28 で seed した module='platform' を Guard が module='system' に
    // 上書きしてしまう（UI のモジュール別グルーピングが壊れる）。

    static {
        PermissionCode.registerAll(SystemPermissions.class, "system");
    }

    SystemPermissions() {}
}
