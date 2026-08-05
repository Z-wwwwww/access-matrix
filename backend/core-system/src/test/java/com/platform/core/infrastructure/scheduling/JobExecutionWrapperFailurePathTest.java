package com.platform.core.infrastructure.scheduling;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.scheduling.JobContext;
import com.platform.core.common.scheduling.ScheduledJob;
import com.platform.core.common.scheduling.TriggerType;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import com.platform.core.infrastructure.scheduling.entity.CoreJobLogEntity;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobLogMapper;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The wrapper's bookkeeping (distributed lock + {@link RequestContext}) must survive
 * a DB failure on the bookkeeping itself.
 *
 * <p>The wrapper acquires the {@code core_job_lock} row for a non-concurrent job and
 * only then inserts the RUNNING log line. That insert used to sit OUTSIDE the
 * try/finally, so if it threw — the DB going away for a moment is exactly the kind
 * of hiccup this class's javadoc promises to absorb ("スケジューラスレッドは殺さない") —
 * neither {@code lockService.release} nor {@code RequestContext.clear} ran:
 *
 * <ul>
 *   <li>the lock stayed held until its {@code lock_until} lease expired
 *       ({@code max_run_seconds}, 300s by default), so every node skipped the job
 *       silently for the next 5 minutes;</li>
 *   <li>the pooled scheduler thread kept a {@code tenant='system'} context, which is
 *       the tenant-scoping BYPASS signal for {@code MybatisPlusConfig.ignoreTable} —
 *       leaking it onto a thread the same pool hands to {@code runNow} is exactly
 *       the state this codebase treats as a leak (cf. {@code DynamicJobScheduler
 *       .loadEnabled}, which snapshots and restores for the same reason).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobExecutionWrapperFailurePathTest {

    @Mock ScheduledJobRegistry registry;
    @Mock JobLockService lockService;
    @Mock CoreJobMapper jobMapper;
    @Mock CoreJobLogMapper logMapper;

    private JobExecutionWrapper wrapper;
    private ScheduledJob job;

    private static final String CODE = "demo.heartbeat";
    private static final String TENANT = "system";
    private static final String LOCK = CODE + "::" + TENANT;

    @BeforeEach
    void setUp() {
        wrapper = new JobExecutionWrapper(registry, lockService, jobMapper, logMapper);
        job = mock(ScheduledJob.class);

        CoreJobEntity cfg = new CoreJobEntity();
        cfg.setJobCode(CODE);
        cfg.setTenantId(TENANT);
        cfg.setConcurrent(0);           // non-concurrent → takes the lock
        cfg.setMaxRunSeconds(300);

        when(registry.find(CODE)).thenReturn(Optional.of(job));
        when(jobMapper.selectOne(any(Wrapper.class))).thenReturn(cfg);
        when(lockService.tryAcquire(eq(LOCK), anyInt())).thenReturn(true);
        when(lockService.nodeId()).thenReturn("node-test");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void lockAndContextAreReleasedWhenTheRunningLogInsertFails() {
        when(logMapper.insert(any(CoreJobLogEntity.class)))
                .thenThrow(new RuntimeException("connection reset by peer"));

        wrapper.execute(CODE, TENANT, TriggerType.CRON, null);

        verify(lockService).release(LOCK);
        assertThat(RequestContext.current())
                .as("the system-tenant scoping-bypass context must not leak onto the pooled thread")
                .isNull();
    }

    @Test
    void aFailedRunIsStillRecordedOnTheConfigRowWhenTheLogWriteFails() {
        when(logMapper.insert(any(CoreJobLogEntity.class)))
                .thenThrow(new RuntimeException("connection reset by peer"));

        wrapper.execute(CODE, TENANT, TriggerType.CRON, null);

        // No RUNNING row exists, so there is nothing to finalize — but the operator
        // still needs to see that the fire failed, and core_job.last_status is the
        // only surface left.
        verify(jobMapper).update(eq(null), any(Wrapper.class));
    }

    @Test
    void aHealthyRunStillReleasesTheLockAndClearsTheContext() throws Exception {
        when(logMapper.insert(any(CoreJobLogEntity.class))).thenReturn(1);

        wrapper.execute(CODE, TENANT, TriggerType.CRON, null);

        verify(job).execute(any(JobContext.class));
        verify(lockService).release(LOCK);
        assertThat(RequestContext.current()).isNull();
    }
}
