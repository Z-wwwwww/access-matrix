package com.platform.system.job.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.scheduling.DynamicJobScheduler;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
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
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A job config change must leave the caller's {@link RequestContext} intact.
 *
 * <p>{@code JobAdminService.update} / {@code setEnabled} end by calling
 * {@code DynamicJobScheduler.reconcileNow()}, which is {@code synchronized} and runs on
 * the CALLER's thread. Its {@code loadEnabled()} step needs the system tenant to read
 * across tenants — but it used to install that identity over the caller's and then
 * {@code clear()} it outright, so the request continued with no context.
 *
 * <p>That lands exactly where it hurts: {@code OpLogAspect} builds its record in a
 * {@code finally} block AFTER the annotated method returns, reading userId / username /
 * tenantId from {@code RequestContext} at that point. The three job endpoints whose
 * service methods end with {@code reconcile()} — {@code job.config}, {@code job.enable},
 * {@code job.disable} — therefore recorded a privileged scheduler change with no actor.
 *
 * <p>These tests drive the real scheduler bean (only its mapper is mocked) so the
 * save/restore actually gets exercised rather than stubbed away.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobAdminServiceContextTest {

    @Mock CoreJobMapper jobMapper;
    @Mock CoreJobLogMapper logMapper;

    private DynamicJobScheduler scheduler;
    private JobAdminService service;

    private static CoreJobEntity job() {
        CoreJobEntity e = new CoreJobEntity();
        e.setId("job-1");
        e.setJobCode("demo.cleanup");
        e.setTenantId("system");
        e.setCron("0 0 3 * * *");
        e.setMark(1);
        e.setEnabled(1);
        return e;
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        // Real scheduler: no task scheduler needed for reconcile's read path, and an
        // empty job list means schedule() is never reached.
        scheduler = new DynamicJobScheduler(
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler(),
                jobMapper,
                mock(com.platform.core.infrastructure.scheduling.JobExecutionWrapper.class));
        when(jobMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(jobMapper.selectById("job-1")).thenReturn(job());

        ObjectProvider<DynamicJobScheduler> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(scheduler);
        service = new JobAdminService(jobMapper, logMapper, provider);

        RequestContext.set("system", "01ARZ3NDEKTSV4RRFFQ69G5FAV", "ops-alice", Locale.JAPAN, "trace-1");
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    private void assertCallerContextIntact() {
        assertThat(RequestContext.current())
                .as("OpLogAspect reads the actor AFTER the method returns — a cleared "
                        + "context means the audit row has no actor")
                .isNotNull();
        assertThat(RequestContext.userId()).isEqualTo("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        assertThat(RequestContext.current().getUsername()).isEqualTo("ops-alice");
        assertThat(RequestContext.tenantId()).isEqualTo("system");
        assertThat(RequestContext.current().getTraceId()).isEqualTo("trace-1");
        assertThat(RequestContext.locale()).isEqualTo(Locale.JAPAN);
    }

    @Test
    void setEnabled_leavesTheCallersContextIntact() {
        service.setEnabled("job-1", false);

        assertCallerContextIntact();
    }

    @Test
    void update_leavesTheCallersContextIntact() {
        service.update("job-1", new com.platform.system.job.dto.JobDto.UpdateRequest(
                null, "0 30 4 * * *", null, null, null));

        assertCallerContextIntact();
    }

    @Test
    void reconcileFromABackgroundThreadWithNoContext_leavesItAbsent() {
        // The @Scheduled path has no caller context; it must not be left holding the
        // reconciler identity either.
        RequestContext.clear();

        scheduler.reconcileNow();

        assertThat(RequestContext.current()).isNull();
    }
}
