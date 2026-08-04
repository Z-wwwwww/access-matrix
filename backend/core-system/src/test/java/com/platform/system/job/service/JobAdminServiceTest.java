package com.platform.system.job.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.time.AppTime;
import com.platform.core.infrastructure.scheduling.DynamicJobScheduler;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobLogMapper;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobMapper;
import com.platform.core.common.result.PageResult;
import com.platform.system.job.dto.JobDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins that the admin list's "next fire time" is computed on the BUSINESS clock.
 *
 * <p>{@code DynamicJobScheduler} schedules with {@code new CronTrigger(cron,
 * AppTime.zone())}, so a cron of {@code 0 30 3 * * *} fires at 03:30 in
 * {@link AppTime#zone()}. The console column has to agree. It used to be computed
 * as {@code CronExpression.parse(cron).next(OffsetDateTime.now())}, which reads the
 * cron fields against the JVM's DEFAULT zone — on a UTC host that rendered 03:30
 * UTC (= 12:30 JST) for the seeded {@code core:outbox-retention} job, 9 hours off
 * from reality and indistinguishable from a correct value by eye.
 *
 * <p>The assertion is deliberately expressed as a wall-clock reading in
 * {@code AppTime.zone()} rather than a comparison against a recomputed expected
 * instant — that way it fails on a machine whose OS timezone differs from the
 * business timezone (which is the only configuration where the bug shows) instead
 * of being tautological.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobAdminServiceTest {

    @Mock CoreJobMapper jobMapper;
    @Mock CoreJobLogMapper logMapper;

    private ObjectProvider<DynamicJobScheduler> schedulerProvider;
    private JobAdminService service;
    /** The single row list() will page over. Tests reassign it instead of re-stubbing
        selectPage — a second when(mock.selectPage(...)) would re-invoke the first
        answer with null args. */
    private CoreJobEntity row;

    @SuppressWarnings("unchecked")
    private void build(DynamicJobScheduler scheduler) {
        schedulerProvider = (ObjectProvider<DynamicJobScheduler>) mock(ObjectProvider.class);
        when(schedulerProvider.getIfAvailable()).thenReturn(scheduler);
        service = new JobAdminService(jobMapper, logMapper, schedulerProvider);
    }

    @BeforeEach
    void stubPage() {
        row = job("core:outbox-retention", "0 30 3 * * *", 1);
        // list() pages over whatever selectPage returns; we only care about toView.
        when(jobMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<CoreJobEntity> p = inv.getArgument(0);
            p.setRecords(List.of(row));
            p.setTotal(1);
            return p;
        });
    }

    private static CoreJobEntity job(String code, String cron, int enabled) {
        CoreJobEntity e = new CoreJobEntity();
        e.setId("ULID-1");
        e.setTenantId("system");
        e.setJobCode(code);
        e.setName(code);
        e.setCron(cron);
        e.setEnabled(enabled);
        e.setMark(1);
        return e;
    }

    private JobDto.View onlyRow() {
        PageResult<JobDto.View> r = service.list(1, 20, null);
        assertThat(r.records()).hasSize(1);
        return r.records().get(0);
    }

    @Test
    void nextFireTime_readsAsTheCronsWallClockInTheBusinessZone_viaScheduler() {
        // Real scheduler bean present → the zone-aware helper is the authority.
        build(new DynamicJobScheduler(null, null, null));

        JobDto.View v = onlyRow();

        assertThat(v.nextFireTime()).isNotNull();
        var jst = v.nextFireTime().atZoneSameInstant(AppTime.zone());
        assertThat(jst.getHour()).as("03:30 in the business zone, whatever the host TZ is").isEqualTo(3);
        assertThat(jst.getMinute()).isEqualTo(30);
    }

    @Test
    void nextFireTime_staysOnTheBusinessClockWhenTheSchedulerIsDisabled() {
        // app.scheduler.enabled=false → no bean; the fallback must use the same zone,
        // not silently drop back to the JVM default.
        build(null);

        JobDto.View v = onlyRow();

        assertThat(v.nextFireTime()).isNotNull();
        var jst = v.nextFireTime().atZoneSameInstant(AppTime.zone());
        assertThat(jst.getHour()).isEqualTo(3);
        assertThat(jst.getMinute()).isEqualTo(30);
    }

    @Test
    void nextFireTime_isNullForADisabledJob() {
        build(new DynamicJobScheduler(null, null, null));
        row = job("demo:heartbeat", "0 * * * * *", 0);

        assertThat(onlyRow().nextFireTime()).isNull();
    }

    @Test
    void nextFireTime_isNullForAnInvalidCron() {
        build(new DynamicJobScheduler(null, null, null));
        row = job("broken", "not a cron", 1);

        assertThat(onlyRow().nextFireTime()).isNull();
    }
}
