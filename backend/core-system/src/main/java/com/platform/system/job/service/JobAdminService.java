package com.platform.system.job.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.time.AppTime;
import com.platform.core.infrastructure.scheduling.DynamicJobScheduler;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import com.platform.core.infrastructure.scheduling.entity.CoreJobLogEntity;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobLogMapper;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobMapper;
import com.platform.system.job.dto.JobDto;
import com.platform.system.job.dto.JobLogDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 定時任務の管理 API サービス。設定編集 / 起停 / 即時実行 / ログ照会。
 *
 * <p>読み書きは <b>呼び出し元の租户コンテキスト</b>で走る（テナント管理者は自分の行のみ、
 * プラットフォーム 'system' 管理者は全行）。設定変更後はこのノードで
 * {@link DynamicJobScheduler#reconcileNow()} を呼んで即時反映する（他ノードは
 * reconcile 間隔で収束）。スケジューラは {@code app.scheduler.enabled=false} で不在に
 * なりうるので {@link ObjectProvider} で受ける。
 */
@Service
public class JobAdminService {

    private final CoreJobMapper jobMapper;
    private final CoreJobLogMapper logMapper;
    private final ObjectProvider<DynamicJobScheduler> schedulerProvider;

    public JobAdminService(CoreJobMapper jobMapper, CoreJobLogMapper logMapper,
                           ObjectProvider<DynamicJobScheduler> schedulerProvider) {
        this.jobMapper = jobMapper;
        this.logMapper = logMapper;
        this.schedulerProvider = schedulerProvider;
    }

    public PageResult<JobDto.View> list(long page, long size, String keyword) {
        Page<CoreJobEntity> p = new Page<>(page, size);
        QueryWrapper<CoreJobEntity> w = new QueryWrapper<CoreJobEntity>()
                .eq("mark", 1)
                .orderByAsc("job_code");   // scope column dropped in V42 — order by code only
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("job_code", keyword).or().like("name", keyword));
        }
        Page<CoreJobEntity> result = jobMapper.selectPage(p, w);
        List<JobDto.View> records = result.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public PageResult<JobLogDto.View> logs(String jobCode, long page, long size) {
        Page<CoreJobLogEntity> p = new Page<>(page, size);
        QueryWrapper<CoreJobLogEntity> w = new QueryWrapper<CoreJobLogEntity>()
                .eq("mark", 1)
                .orderByDesc("id");          // ULID id ≈ 時系列降順
        if (jobCode != null && !jobCode.isBlank()) {
            w.eq("job_code", jobCode);
        }
        Page<CoreJobLogEntity> result = logMapper.selectPage(p, w);
        List<JobLogDto.View> records = result.getRecords().stream().map(this::toLogView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    /** cron / max-run / concurrent / remark を変更（job_code は不変）。 */
    @Transactional
    public void update(String id, JobDto.UpdateRequest req) {
        CoreJobEntity row = require(id);
        if (!CronExpression.isValidExpression(req.cron())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Invalid cron expression: " + req.cron());
        }
        UpdateWrapper<CoreJobEntity> u = new UpdateWrapper<CoreJobEntity>()
                .eq("id", id).eq("mark", 1)
                .set("cron", req.cron())
                .set("update_time", OffsetDateTime.now());
        if (req.name() != null && !req.name().isBlank()) u.set("name", req.name());
        if (req.maxRunSeconds() != null) u.set("max_run_seconds", req.maxRunSeconds());
        if (req.concurrent() != null) u.set("concurrent", req.concurrent());
        if (req.remark() != null) u.set("remark", req.remark());
        jobMapper.update(null, u);
        reconcile();
    }

    @Transactional
    public void setEnabled(String id, boolean enabled) {
        require(id);
        jobMapper.update(null, new UpdateWrapper<CoreJobEntity>()
                .eq("id", id).eq("mark", 1)
                .set("enabled", enabled ? 1 : 0)
                .set("update_time", OffsetDateTime.now()));
        reconcile();
    }

    /** 即時実行。設定行の租户で {@code trigger_type=manual} 実行を投入する。 */
    public void runNow(String id) {
        CoreJobEntity row = require(id);
        DynamicJobScheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Scheduler is disabled (app.scheduler.enabled=false)");
        }
        scheduler.runNow(row.getJobCode(), row.getTenantId(), RequestContext.userId());
    }

    private CoreJobEntity require(String id) {
        CoreJobEntity row = jobMapper.selectById(id);   // 租户拦截器でスコープ済み
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Job not found: " + id);
        }
        return row;
    }

    private void reconcile() {
        DynamicJobScheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler != null) {
            scheduler.reconcileNow();
        }
    }

    /**
     * Next fire time for the admin list, interpreted in the BUSINESS timezone
     * ({@link AppTime#zone()}) — the same clock {@code DynamicJobScheduler}
     * schedules on ({@code new CronTrigger(cron, AppTime.zone())}).
     *
     * <p>This used to be {@code CronExpression.parse(cron).next(OffsetDateTime.now())},
     * which reads the cron fields against the JVM's DEFAULT zone. On any host whose
     * OS timezone differs from {@code app.timezone} that silently disagrees with when
     * the job really runs: {@code core:outbox-retention} is {@code 0 30 3 * * *} and
     * fires at 03:30 Asia/Tokyo, but on a UTC host the console rendered 03:30 UTC
     * (= 12:30 JST) — 9 hours off, with nothing to hint the number was wrong.
     * Prefer the scheduler's own {@code nextFireTime} helper (written for exactly
     * this display and already zone-explicit) so there is one authority; fall back
     * to the identical computation when the scheduler bean is absent
     * ({@code app.scheduler.enabled=false}).
     */
    private OffsetDateTime nextFireTimeOf(CoreJobEntity e) {
        if (!Integer.valueOf(1).equals(e.getEnabled())
                || e.getCron() == null
                || !CronExpression.isValidExpression(e.getCron())) {
            return null;
        }
        DynamicJobScheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler != null) {
            return scheduler.nextFireTime(e.getCron());
        }
        ZonedDateTime next = CronExpression.parse(e.getCron()).next(ZonedDateTime.now(AppTime.zone()));
        return next == null ? null : next.toOffsetDateTime();
    }

    private JobDto.View toView(CoreJobEntity e) {
        OffsetDateTime next = nextFireTimeOf(e);
        return new JobDto.View(
                e.getId(), e.getTenantId(), e.getJobCode(), e.getName(),
                e.getCron(), e.getEnabled(), e.getConcurrent(), e.getMaxRunSeconds(),
                e.getLastFireTime(), e.getLastStatus(), e.getLastDurationMs(), next,
                e.getRemark(), e.getCreateTime(), e.getUpdateTime());
    }

    private JobLogDto.View toLogView(CoreJobLogEntity e) {
        return new JobLogDto.View(
                e.getId(), e.getJobCode(), e.getTriggerType(), e.getStatus(), e.getNodeId(),
                e.getStartTime(), e.getEndTime(), e.getDurationMs(), e.getError(), e.getTriggeredBy());
    }
}
