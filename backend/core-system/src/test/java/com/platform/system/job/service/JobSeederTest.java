package com.platform.system.job.service;

import com.platform.core.common.scheduling.JobContext;
import com.platform.core.common.scheduling.ScheduledJob;
import com.platform.core.infrastructure.scheduling.ScheduledJobRegistry;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import com.platform.core.infrastructure.scheduling.mapper.CoreJobMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the seeder's "revive a soft-deleted config row" contract.
 *
 * <p>The historic bug: {@code upsert} looked the row up with
 * {@code jobMapper.selectOne(new QueryWrapper<>()...)}. {@code mark} is a
 * {@code @TableLogic} column, so MyBatis-Plus appends {@code AND mark = 1} to
 * every wrapper SELECT — a row soft-deleted by
 * {@code JobRegistrySyncGuard.removeOrphans} was therefore invisible, the
 * documented revive branch could never run, and the seeder fell through to the
 * INSERT. The admin's cron / enabled / concurrent / max_run_seconds / name were
 * silently reset to the code defaults on the next boot, and the mark=0 row was
 * stranded forever. (The revive UPDATE was doubly dead: a wrapper UPDATE carries
 * the same {@code AND mark = 1} guard.)
 *
 * <p>Concretely triggered by any boot where a {@code ScheduledJob} bean is
 * temporarily absent — a reverted branch, a module left out of the build — which
 * soft-deletes its row; the boot after that silently loses the tuned cron.
 */
@ExtendWith(MockitoExtension.class)
class JobSeederTest {

    private static final String CODE = "core:outbox-retention";

    @Mock CoreJobMapper jobMapper;

    private JobSeeder seeder() {
        return new JobSeeder(new ScheduledJobRegistry(List.of(job())), jobMapper);
    }

    private static ScheduledJob job() {
        return new ScheduledJob() {
            @Override public String code() { return CODE; }
            @Override public String defaultCron() { return "0 30 3 * * *"; }
            @Override public void execute(JobContext ctx) { }
        };
    }

    private static CoreJobEntity row(int mark, String cron) {
        CoreJobEntity e = new CoreJobEntity();
        e.setId("ULID-1");
        e.setTenantId("system");
        e.setJobCode(CODE);
        e.setCron(cron);
        e.setMark(mark);
        return e;
    }

    @Test
    void softDeletedRow_isRevived_notReinsertedWithDefaults() {
        when(jobMapper.findAnyByCode("system", CODE)).thenReturn(row(0, "0 0 5 * * *"));
        when(jobMapper.revive(eq("ULID-1"), any())).thenReturn(1);

        assertThat(seeder().seedAll()).isZero();

        verify(jobMapper).revive(eq("ULID-1"), any());
        // The admin's tuned cron survives: no fresh row with the code default.
        verify(jobMapper, never()).insert(any(CoreJobEntity.class));
    }

    @Test
    void liveRow_isLeftAlone() {
        when(jobMapper.findAnyByCode("system", CODE)).thenReturn(row(1, "0 0 5 * * *"));

        assertThat(seeder().seedAll()).isZero();

        verify(jobMapper, never()).revive(any(), any());
        verify(jobMapper, never()).insert(any(CoreJobEntity.class));
    }

    @Test
    void missingRow_isSeededWithTheCodeDefaults() {
        when(jobMapper.findAnyByCode("system", CODE)).thenReturn(null);

        assertThat(seeder().seedAll()).isEqualTo(1);

        verify(jobMapper).insert(any(CoreJobEntity.class));
        verify(jobMapper, never()).revive(any(), any());
    }
}
