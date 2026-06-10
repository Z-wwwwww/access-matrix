package com.platform.core.infrastructure.scheduling;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.scheduling.TriggerType;
import com.platform.core.common.time.AppTime;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 動的スケジューラ本体。{@code @Scheduled} を静的に書く代わりに、{@code core_job} の
 * 設定行から実行時に {@link CronTrigger} を組み立て、メモリ上の {@link ScheduledFuture}
 * 登録表で起停・再スケジュールする。
 *
 * <p><b>キー</b>は {@code jobCode::tenantId}（PLATFORM は tenantId="system"、
 * TENANT は租户ごと）。
 *
 * <p><b>クラスタ越しの設定伝播 = reconciler</b>。{@link #scheduledReconcile()} が
 * {@code app.scheduler.reconcile-interval-ms}（既定 15s）ごとに、system 租户で
 * 全租户横断に enabled 行を読み、メモリ登録表と差分を取る（無→schedule / 多→cancel /
 * cron 変→reschedule）。これが唯一のスケジュール権威。設定変更したノードは
 * {@link #reconcileNow()} を呼べば即時反映、他ノードは最大 1 間隔で収束する。
 * Redis 等の追加依存は不要。
 *
 * <p>{@code app.scheduler.enabled=false} で無効化（テスト等）。
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", matchIfMissing = true)
public class DynamicJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicJobScheduler.class);
    private static final String SYSTEM_TENANT = "system";

    private final ThreadPoolTaskScheduler taskScheduler;
    private final CoreJobMapper jobMapper;
    private final JobExecutionWrapper wrapper;

    /** key → 現在スケジュール中の future。 */
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    /** key → 現在スケジュール中の cron（差分判定用）。 */
    private final Map<String, String> scheduledCron = new ConcurrentHashMap<>();

    public DynamicJobScheduler(ThreadPoolTaskScheduler taskScheduler,
                               CoreJobMapper jobMapper,
                               JobExecutionWrapper wrapper) {
        this.taskScheduler = taskScheduler;
        this.jobMapper = jobMapper;
        this.wrapper = wrapper;
    }

    /** 定期 reconcile（クラスタ収束のバックストップ）。 */
    @Scheduled(initialDelayString = "${app.scheduler.reconcile-initial-delay-ms:10000}",
            fixedDelayString = "${app.scheduler.reconcile-interval-ms:15000}")
    public void scheduledReconcile() {
        reconcileNow();
    }

    /**
     * DB の enabled 設定とメモリ登録表を一致させる。同期 guard / 管理 API からも呼ぶ。
     * 失敗してもスケジューラは死なせない（次の tick が再試行）。
     */
    public synchronized void reconcileNow() {
        try {
            Map<String, CoreJobEntity> desired = loadEnabled();

            int scheduled = 0, rescheduled = 0, cancelled = 0;

            for (Map.Entry<String, CoreJobEntity> e : desired.entrySet()) {
                String key = e.getKey();
                CoreJobEntity row = e.getValue();
                String currentCron = scheduledCron.get(key);
                if (currentCron == null) {
                    if (schedule(key, row)) scheduled++;
                } else if (!currentCron.equals(row.getCron())) {
                    cancel(key);
                    if (schedule(key, row)) rescheduled++;
                }
            }

            // desired に無いキーは停止/削除されたもの → cancel。
            for (String key : List.copyOf(scheduledCron.keySet())) {
                if (!desired.containsKey(key)) {
                    cancel(key);
                    cancelled++;
                }
            }

            if (scheduled + rescheduled + cancelled > 0) {
                log.info("[scheduler] reconcile: scheduled={} rescheduled={} cancelled={} (active={})",
                        scheduled, rescheduled, cancelled, scheduledCron.size());
            }
        } catch (Exception ex) {
            log.warn("[scheduler] reconcile failed: {}", ex.getMessage());
        }
    }

    /**
     * 即時実行（run-now）。cron スケジュール登録表には一切触れないので排程は乱れない。
     * 別スレッドで包装器を回し、{@code trigger_type=manual} で記録する。
     */
    public void runNow(String jobCode, String tenantId, String triggeredBy) {
        taskScheduler.execute(() -> wrapper.execute(jobCode, tenantId, TriggerType.MANUAL, triggeredBy));
    }

    /** UI 表示用：cron 式から次回発火時刻を即時計算（DB には保存しない）。
     *  cron のフィールドは業務時間（{@link AppTime#ZONE}）の壁時計として解釈する。 */
    public OffsetDateTime nextFireTime(String cron) {
        try {
            CronExpression expr = CronExpression.parse(cron);
            ZonedDateTime next = expr.next(ZonedDateTime.now(AppTime.ZONE));
            return next == null ? null : next.toOffsetDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    /** system 租户で横断に enabled=1 行を読み、key→row のマップにする。 */
    private Map<String, CoreJobEntity> loadEnabled() {
        RequestContext.set(SYSTEM_TENANT, "system", "job-reconciler", Locale.ROOT, null);
        try {
            List<CoreJobEntity> rows = jobMapper.selectList(new QueryWrapper<CoreJobEntity>()
                    .eq("enabled", 1).eq("mark", 1));
            Map<String, CoreJobEntity> out = new ConcurrentHashMap<>();
            for (CoreJobEntity r : rows) {
                out.put(r.getJobCode() + "::" + r.getTenantId(), r);
            }
            return out;
        } finally {
            RequestContext.clear();
        }
    }

    /** key を cron でスケジュール登録。cron 不正なら WARN してスキップ（false）。 */
    private boolean schedule(String key, CoreJobEntity row) {
        String cron = row.getCron();
        if (!CronExpression.isValidExpression(cron)) {
            log.warn("[scheduler] invalid cron '{}' for {} — not scheduled", cron, key);
            return false;
        }
        String jobCode = row.getJobCode();
        String tenantId = row.getTenantId();
        try {
            ScheduledFuture<?> f = taskScheduler.schedule(
                    () -> wrapper.execute(jobCode, tenantId, TriggerType.CRON, null),
                    new CronTrigger(cron, AppTime.ZONE));
            if (f == null) {
                log.warn("[scheduler] schedule rejected for {} (cron {})", key, cron);
                return false;
            }
            futures.put(key, f);
            scheduledCron.put(key, cron);
            return true;
        } catch (Exception e) {
            log.warn("[scheduler] schedule failed for {} (cron {}): {}", key, cron, e.getMessage());
            return false;
        }
    }

    /** key のスケジュールを取り消す。 */
    private void cancel(String key) {
        ScheduledFuture<?> f = futures.remove(key);
        scheduledCron.remove(key);
        if (f != null) {
            f.cancel(false);
        }
    }
}
