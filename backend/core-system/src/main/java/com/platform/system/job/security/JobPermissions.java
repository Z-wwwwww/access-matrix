package com.platform.system.job.security;

import com.platform.core.common.security.PermissionCode;
import org.springframework.stereotype.Component;

/**
 * 定時タスク管理の権限コード常量。
 *
 * <p>タスク管理は<b>平台運維(Platform Admin)専用</b>機能。コードは {@code platform:} 名前空間の
 * 3 セグメント形にして module="platform" で登録する。これにより：
 * <ul>
 *   <li>{@code *:*}(PLATFORM_ADMIN) / {@code platform:*} 保有者は通る</li>
 *   <li>業務租户の超管({@code tenant:*})は {@code platform:} を含まないので <b>通らない(403)</b></li>
 * </ul>
 * 3 セグメントコードは専用 *Permissions クラス + module="platform" が必須
 * （{@code PlatformPermissions} と同じ理由。{@code SystemPermissions} の Javadoc 参照）。
 */
@Component
public final class JobPermissions {

    /** 一覧 + 実行ログ閲覧。 */
    public static final String JOB_READ = "platform:job:read";
    /** cron / max-run / concurrent / remark の変更。 */
    public static final String JOB_CONFIG = "platform:job:config";
    /** 即時実行（run-now）。 */
    public static final String JOB_RUN = "platform:job:run";
    /** 有効/無効（起停）。 */
    public static final String JOB_TOGGLE = "platform:job:toggle";

    static {
        PermissionCode.registerAll(JobPermissions.class, "platform");
    }

    JobPermissions() {}
}
