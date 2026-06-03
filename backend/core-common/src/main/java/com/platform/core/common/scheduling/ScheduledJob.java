package com.platform.core.common.scheduling;

/**
 * 動的に設定・管理できる定時任務の SPI。
 *
 * <p><b>コード登録 + 設定可調モデル。</b> ジョブのロジックはこの interface を実装した
 * Spring bean として書く。一意の {@link #code()} を持ち、起動時に
 * {@code JobRegistrySyncGuard} が {@code core_job} 設定テーブルへ同期する
 * （{@code PermissionConsistencyGuard} がパーミッションコードを同期するのと同じ仕組み）。
 * cron / 有効無効 などの可変設定は <b>DB が真実の源</b>になり、管理画面から
 * 変更・起停・即時実行できる。<b>任意のジョブを画面から新規作成することはできない</b>
 * （ロジックがコード側にしか無いため）。
 *
 * <p>実装は {@code core-infrastructure} 配下の {@code JobExecutionWrapper} 経由でのみ
 * 呼ばれる。包装器が分布式ロック取得・実行ログ記録・{@code RequestContext} 設定を行うので、
 * {@link #execute(JobContext)} は純粋に業務ロジックだけを書けばよい。例外を投げても
 * スケジューラスレッドは死なない（包装器が捕捉してログへ FAIL を記録する）。
 *
 * <p>実装例（{@code business-demo} 参照）：
 * <pre>{@code
 *   @Component
 *   public class CleanupStaleTasksJob implements ScheduledJob {
 *       public String code()        { return "demo:cleanup-stale-tasks"; }
 *       public String defaultCron() { return "0 0 3 * * *"; }   // 毎日 03:00
 *       public void execute(JobContext ctx) { ... }
 *   }
 * }</pre>
 *
 * <p>定時タスクはすべて<b>システム(プラットフォーム)レベル</b>。租户ごとの区別は無く、
 * system コンテキストで実行される（管理は平台運維が行う）。
 */
public interface ScheduledJob {

    /**
     * 一意のジョブコード。{@code module:verb-noun} 形式を推奨（例
     * {@code "demo:cleanup-stale-tasks"}）。重複は起動時に fail-fast。
     * {@code core_job.job_code} のキーになる。
     */
    String code();

    /**
     * 初回同期時に {@code core_job.cron} へ書き込む既定 cron 式（6 フィールドの
     * Spring cron）。同期後は DB 値が優先され、ここを変えても既存設定行は上書きしない。
     */
    String defaultCron();

    /** 初回同期時の既定有効状態。既定は無効（管理者が明示的に有効化する）。 */
    default boolean enabledByDefault() {
        return false;
    }

    /**
     * 同一ジョブの重複実行を許すか。{@code false}（既定）なら実行中の間は次の発火を
     * スキップする（分布式ロックがそのガードを兼ねる）。
     */
    default boolean concurrentAllowed() {
        return false;
    }

    /**
     * 想定最大実行秒数。分布式ロックの保持時間（= クラッシュ時のロック自動失効時限）に
     * 使う。実際の実行時間より十分大きく設定すること。
     */
    default int maxRunSeconds() {
        return 300;
    }

    /** 業務ロジック本体。包装器の中で 1 回の実行ごとに呼ばれる。 */
    void execute(JobContext ctx) throws Exception;
}
