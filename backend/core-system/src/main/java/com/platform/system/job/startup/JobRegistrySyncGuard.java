package com.platform.system.job.startup;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.scheduling.ScheduledJob;
import com.platform.core.infrastructure.scheduling.DynamicJobScheduler;
import com.platform.core.infrastructure.scheduling.ScheduledJobRegistry;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobMapper;
import com.platform.system.job.service.JobSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 起動時に「コード側 {@link ScheduledJob} bean ↔ {@code core_job} 設定行」を一致させる
 * （{@code PermissionConsistencyGuard} のジョブ版）。
 *
 * <p>段階：
 * <ol>
 *   <li><b>播種</b>：{@link JobSeeder#seedAll()} で不足している設定行を作る
 *       （PLATFORM=1 行 / TENANT=活动租户ごと）。既存行の cron/enabled は保持。</li>
 *   <li><b>孤児追従</b>：コードに無い {@code job_code} の行は soft delete（mark=0）。
 *       全租户横断（system 租户）で走査。実行ログ ({@code core_job_log}) は監査用に残す。</li>
 *   <li><b>スケジュール</b>：末尾で {@link DynamicJobScheduler#reconcileNow()} を呼び、
 *       1 間隔待たずに enabled ジョブを即スケジュールする。</li>
 * </ol>
 *
 * <p>重複コードの fail-fast は {@link ScheduledJobRegistry} 構築時に済んでいる。
 */
@Component
public class JobRegistrySyncGuard {

    private static final Logger log = LoggerFactory.getLogger(JobRegistrySyncGuard.class);
    private static final String SYSTEM_TENANT = "system";

    private final ScheduledJobRegistry registry;
    private final JobSeeder seeder;
    private final CoreJobMapper jobMapper;
    /** スケジューラは {@code app.scheduler.enabled=false} で不在になりうる。 */
    private final ObjectProvider<DynamicJobScheduler> schedulerProvider;

    public JobRegistrySyncGuard(ScheduledJobRegistry registry, JobSeeder seeder,
                                CoreJobMapper jobMapper,
                                ObjectProvider<DynamicJobScheduler> schedulerProvider) {
        this.registry = registry;
        this.seeder = seeder;
        this.jobMapper = jobMapper;
        this.schedulerProvider = schedulerProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        int seeded = seeder.seedAll();
        int orphans = removeOrphans();

        log.info("[JobGuard] OK — registered={}, seeded={}, orphans_removed={}",
                registry.all().size(), seeded, orphans);

        DynamicJobScheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler != null) {
            scheduler.reconcileNow();
        }
    }

    /** コード宣言に無い job_code の設定行を全租户横断で soft delete。戻り値は削除行数。 */
    private int removeOrphans() {
        Set<String> known = registry.all().stream()
                .map(ScheduledJob::code).collect(Collectors.toCollection(TreeSet::new));

        RequestContext.set(SYSTEM_TENANT, "system", "job-guard", Locale.ROOT, null);
        try {
            List<CoreJobEntity> rows = jobMapper.selectList(new QueryWrapper<CoreJobEntity>()
                    .select("id", "job_code").eq("mark", 1));
            int removed = 0;
            LocalDateTime now = LocalDateTime.now();
            for (CoreJobEntity row : rows) {
                if (!known.contains(row.getJobCode())) {
                    jobMapper.update(null, new UpdateWrapper<CoreJobEntity>()
                            .eq("id", row.getId()).eq("mark", 1)
                            .set("mark", 0).set("update_time", now));
                    log.warn("[JobGuard] removed orphan job config '{}' (no longer declared in code)",
                            row.getJobCode());
                    removed++;
                }
            }
            return removed;
        } finally {
            RequestContext.clear();
        }
    }
}
