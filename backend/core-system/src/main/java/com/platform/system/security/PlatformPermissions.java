package com.platform.system.security;

import com.platform.core.common.security.PermissionCode;
import org.springframework.stereotype.Component;

/**
 * {@code platform:} 名前空間の権限コード常量。
 *
 * <p>{@link SystemPermissions} とは分離している理由：
 * <ul>
 *   <li>{@code PermissionCode.registerAll} は 1 クラス = 1 module でしか登録できない。
 *       同じファイルに business 権限と platform 権限を混在させると、
 *       どちらかの module が不正になる。</li>
 *   <li>V28 マイグレーションは {@code module='platform'} で seed しており、
 *       PermissionConsistencyGuard はクラス常量側の module で DB を上書きする。
 *       2 クラスに分けることで {@code system} / {@code platform} のラベルが
 *       両側でちょうど整合する。</li>
 *   <li>UI（Role 編集ドロワーのモジュール別グルーピング）は module 列を見て
 *       「システム」「プラットフォーム」を分けて表示する。クラス分離は
 *       そのままその表示構造に対応している。</li>
 * </ul>
 *
 * <p>命名: コード文字列は 3 セグメント形 {@code platform:tenant:read}。
 * {@link com.platform.core.common.security.PermissionRegistry#put} は最後の
 * {@code :} で resource / action を分割する（resource={@code platform:tenant},
 * action={@code read}）。
 *
 * <p>これらは {@code tenant:*}（業務テナント super-wildcard）に意図的に
 * カバーされない — {@code SUPER_ADMIN of 'acme'} は {@code platform:tenant:read}
 * を呼べない。{@code PLATFORM_ADMIN}（{@code *:*} を持つ）だけが呼べる。
 *
 * <p>{@code @Component} は Spring に強制ロードさせるため。クラスロード →
 * 静的ブロック実行 → PermissionRegistry に登録、という順序を保証する。
 */
@Component
public final class PlatformPermissions {

    public static final String TENANT_READ   = "platform:tenant:read";
    public static final String TENANT_CREATE = "platform:tenant:create";
    public static final String TENANT_DELETE = "platform:tenant:delete";

    static {
        PermissionCode.registerAll(PlatformPermissions.class, "platform");
    }

    PlatformPermissions() {}
}
