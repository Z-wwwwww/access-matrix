package com.platform.core.infrastructure.scheduling;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.scheduling.JobContext;
import com.platform.core.common.scheduling.ScheduledJob;
import com.platform.core.common.scheduling.TriggerType;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import com.platform.core.infrastructure.scheduling.entity.CoreJobLogEntity;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobLogMapper;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 1 回の実行の唯一の収口。cron 発火 / 即時実行 / どのノードでも、ここを通る。
 *
 * <p>流れ（{@code OutboxDispatcher} の「絶対に落ちない」形を踏襲）：
 * <ol>
 *   <li>ジョブ bean を取得（無ければ WARN して return — 孤児設定行）。</li>
 *   <li>設定行を DB から読む（system 租户で横断読み）。無ければ return。</li>
 *   <li>並発ガード：{@code concurrent=0} なら分布式ロックを取得。負けたら<b>静黙 return</b>
 *       （クラスタの落選ノードがログを汚さない）。</li>
 *   <li>実行租户コンテキストを張る（PLATFORM=system / TENANT=実租户）。</li>
 *   <li>RUNNING ログを挿入（独立コミット → 他ノードから即可視）。</li>
 *   <li>業務実行。例外は捕捉し FAIL を記録するだけ — スケジューラスレッドは殺さない。</li>
 *   <li>finally：ログ確定 + core_job.last_* 更新 + ロック解放 + コンテキスト掃除。</li>
 * </ol>
 *
 * <p>事務境界：各 DB 書き込みは {@code @Transactional} を付けず個別コミット
 * （RUNNING 行を即可視にし、業務実行を包装器の事務に閉じ込めない）。
 */
@Component
public class JobExecutionWrapper {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionWrapper.class);

    private static final String SYSTEM_TENANT = "system";
    private static final int ERROR_MAX = 4000;

    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_SUCCESS = 2;
    private static final int STATUS_FAIL = 3;

    private final ScheduledJobRegistry registry;
    private final JobLockService lockService;
    private final CoreJobMapper jobMapper;
    private final CoreJobLogMapper logMapper;

    public JobExecutionWrapper(ScheduledJobRegistry registry, JobLockService lockService,
                               CoreJobMapper jobMapper, CoreJobLogMapper logMapper) {
        this.registry = registry;
        this.lockService = lockService;
        this.jobMapper = jobMapper;
        this.logMapper = logMapper;
    }

    /**
     * 1 回の実行を行う。{@code tenantId} は設定行の租户（PLATFORM は "system"）。
     *
     * @param triggeredBy 即時実行の場合の操作ユーザ id（cron は null）
     */
    public void execute(String jobCode, String tenantId, TriggerType triggerType, String triggeredBy) {
        Optional<ScheduledJob> jobOpt = registry.find(jobCode);
        if (jobOpt.isEmpty()) {
            log.warn("[scheduler] no ScheduledJob bean for code '{}' — skipping (orphan config row?)", jobCode);
            return;
        }
        ScheduledJob job = jobOpt.get();

        CoreJobEntity config = loadConfig(jobCode, tenantId);
        if (config == null) {
            log.warn("[scheduler] no enabled config row for '{}' tenant '{}' — skipping", jobCode, tenantId);
            return;
        }

        boolean concurrent = Integer.valueOf(1).equals(config.getConcurrent());
        int maxRun = config.getMaxRunSeconds() == null ? 300 : config.getMaxRunSeconds();
        String lockName = jobCode + "::" + tenantId;

        boolean acquired = false;
        if (!concurrent) {
            if (!lockService.tryAcquire(lockName, maxRun)) {
                // 他ノード/前回実行が保持中 — 静黙スキップ（ログ行も作らない）。
                return;
            }
            acquired = true;
        }

        // 全ジョブはシステムレベル。実行コンテキストは system 租户（横断バイパス、
        // OutboxDispatcher と同じ）。tenantId は設定行の租户(='system')。
        RequestContext.set(SYSTEM_TENANT, triggeredBy, "job:" + jobCode, Locale.ROOT, "job-" + IdGenerator.ulid());

        OffsetDateTime start = OffsetDateTime.now();
        String logId = insertRunning(jobCode, triggerType, start, triggeredBy);
        int finalStatus = STATUS_SUCCESS;
        String error = null;
        try {
            job.execute(new JobContext(jobCode, tenantId, triggerType, start));
        } catch (Throwable t) {
            finalStatus = STATUS_FAIL;
            error = stringify(t);
            log.warn("[scheduler] job '{}' (tenant {}) failed: {}", jobCode, tenantId, t.toString());
        } finally {
            OffsetDateTime end = OffsetDateTime.now();
            long durationMs = Duration.between(start, end).toMillis();
            finalizeLog(logId, finalStatus, end, durationMs, error);
            updateLastResult(jobCode, tenantId, end, finalStatus, durationMs);
            if (acquired) {
                lockService.release(lockName);
            }
            RequestContext.clear();
        }
    }

    /** 設定行を system 租户で横断読みする（呼び出し元コンテキストに依存しない）。 */
    private CoreJobEntity loadConfig(String jobCode, String tenantId) {
        RequestContext.set(SYSTEM_TENANT, "system", "job-config-load", Locale.ROOT, null);
        try {
            return jobMapper.selectOne(new QueryWrapper<CoreJobEntity>()
                    .eq("job_code", jobCode)
                    .eq("tenant_id", tenantId)
                    .eq("mark", 1)
                    .last("LIMIT 1"));
        } finally {
            RequestContext.clear();
        }
    }

    private String insertRunning(String jobCode, TriggerType triggerType,
                                 OffsetDateTime start, String triggeredBy) {
        CoreJobLogEntity row = new CoreJobLogEntity();
        row.setId(IdGenerator.ulid());
        row.setJobCode(jobCode);
        row.setTriggerType(triggerType.code());
        row.setStatus(STATUS_RUNNING);
        row.setNodeId(lockService.nodeId());
        row.setStartTime(start);
        row.setTriggeredBy(triggeredBy);
        // tenant_id / mark / 監査列は AuditMetaObjectHandler が現在のコンテキストから補填。
        logMapper.insert(row);
        return row.getId();
    }

    private void finalizeLog(String logId, int status, OffsetDateTime end, long durationMs, String error) {
        // @Version (update_time) を踏まないよう UpdateWrapper で明示更新。id は PK 一意。
        logMapper.update(null, new UpdateWrapper<CoreJobLogEntity>()
                .eq("id", logId)
                .set("status", status)
                .set("end_time", end)
                .set("duration_ms", durationMs)
                .set("error", error)
                .set("update_time", end));
    }

    private void updateLastResult(String jobCode, String tenantId, OffsetDateTime end,
                                  int status, long durationMs) {
        jobMapper.update(null, new UpdateWrapper<CoreJobEntity>()
                .eq("job_code", jobCode)
                .eq("tenant_id", tenantId)
                .eq("mark", 1)
                .set("last_fire_time", end)
                .set("last_status", status)
                .set("last_duration_ms", durationMs)
                .set("update_time", end));
    }

    private static String stringify(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName()).append(": ").append(t.getMessage());
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("\n    at ").append(el);
            if (sb.length() > ERROR_MAX) break;
        }
        return sb.length() > ERROR_MAX ? sb.substring(0, ERROR_MAX) : sb.toString();
    }
}
