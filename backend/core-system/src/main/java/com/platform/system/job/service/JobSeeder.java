package com.platform.system.job.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.scheduling.ScheduledJob;
import com.platform.core.infrastructure.scheduling.ScheduledJobRegistry;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * コード側の {@link ScheduledJob} bean を {@code core_job} 設定行へ播種する。
 *
 * <p>定時タスクはすべて<b>システム(プラットフォーム)レベル</b>。租户ごとの区別は無く、
 * 行はすべて {@code tenant_id='system'} の 1 件で、実行も system コンテキストで横断に走る
 * （{@code OutboxDispatcher} と同じ）。管理は平台運維(Platform Admin)が行い、業務租户には
 * 見せない。
 *
 * <p>既存行があれば <b>cron / enabled / concurrent / max_run_seconds / name は触らない</b>
 * （管理者が変更した値が真実の源）。soft-deleted 行のみ復活させる。
 */
@Service
public class JobSeeder {

    private static final Logger log = LoggerFactory.getLogger(JobSeeder.class);
    private static final String SYSTEM_TENANT = "system";

    private final ScheduledJobRegistry registry;
    private final CoreJobMapper jobMapper;

    public JobSeeder(ScheduledJobRegistry registry, CoreJobMapper jobMapper) {
        this.registry = registry;
        this.jobMapper = jobMapper;
    }

    /** 起動時の全播種：全ジョブを system 租户の設定行として upsert。戻り値は新規挿入数。 */
    public int seedAll() {
        RequestContext.set(SYSTEM_TENANT, "system", "job-seeder", Locale.ROOT, null);
        try {
            int inserted = 0;
            for (ScheduledJob job : registry.all()) {
                inserted += upsert(job);
            }
            return inserted;
        } finally {
            RequestContext.clear();
        }
    }

    /** ジョブの設定行を upsert。挿入したら 1、既存更新/no-op なら 0。 */
    private int upsert(ScheduledJob job) {
        // soft-deleted (mark=0) 行も見える手書き SQL を使う。BaseMapper の wrapper
        // SELECT だと @TableLogic の mark に対して MyBatis-Plus が AND mark = 1 を
        // 足すので、孤児追従 (JobRegistrySyncGuard.removeOrphans) が消した行は
        // 決して見つからず、下の INSERT が既定 cron の新規行を作って管理者の設定を
        // 黙って捨てていた（消えた mark=0 行もそのまま残り続ける）。
        CoreJobEntity existing = jobMapper.findAnyByCode(SYSTEM_TENANT, job.code());

        if (existing != null) {
            // 管理者が変えうる値は保持：cron/enabled/concurrent/max_run_seconds に加え、
            // name も管理画面で編集可になったので上書きしない。soft-deleted のみ復活。
            if (Integer.valueOf(0).equals(existing.getMark())
                    && jobMapper.revive(existing.getId(), OffsetDateTime.now()) > 0) {
                log.info("[JobSeeder] revived soft-deleted job config '{}' (cron='{}' preserved)",
                        job.code(), existing.getCron());
            }
            return 0;
        }

        CoreJobEntity row = new CoreJobEntity();
        row.setId(IdGenerator.ulid());
        row.setJobCode(job.code());
        row.setName(job.code());            // 初期表示名は code。管理画面で編集可（以後 seeder は上書きしない）。
        row.setCron(job.defaultCron());
        row.setEnabled(job.enabledByDefault() ? 1 : 0);
        row.setConcurrent(job.concurrentAllowed() ? 1 : 0);
        row.setMaxRunSeconds(job.maxRunSeconds());
        jobMapper.insert(row);              // tenant_id は AuditMetaObjectHandler が 'system' で補填
        log.info("[JobSeeder] seeded job '{}' (cron='{}')", job.code(), job.defaultCron());
        return 1;
    }
}
