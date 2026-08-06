package com.platform.core.infrastructure.event;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.infrastructure.event.entity.DomainEventEntity;
import com.platform.core.infrastructure.event.mapper.DomainEventMapper;
import com.platform.core.infrastructure.scheduling.JobLockService;
import com.platform.core.infrastructure.scheduling.JobSchedulingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The outbox poll must survive the deployment topology the project actually
 * documents.
 *
 * <p>{@code docs/deployment.md} ships an architecture diagram with
 * {@code Backend pod×2~N} and states 「後端無状態 → 水平扩。…pod 之间无差异」.
 * {@link OutboxDispatcher} is a plain {@code @Scheduled} that every 5 seconds runs
 * {@code SELECT … WHERE dispatch_state = 0 ORDER BY id LIMIT n} and only writes the
 * row back <em>after</em> {@link EventDispatchSink#dispatch} returns. There is no
 * claim step, no {@code FOR UPDATE SKIP LOCKED} and — unlike every other periodic
 * task in this codebase, which goes through {@code JobExecutionWrapper} +
 * {@code core_job_lock} — no distributed lock. So all N pods select the same
 * pending rows in the same window and each dispatches all of them.
 *
 * <p>That is not the at-least-once the code advertises.
 * {@code EventDispatchSink}'s javadoc tells whoever plugs in a real consumer that
 * a duplicate means "a crash between downstream-accepted and row-marked-dispatched"
 * — a rare, exceptional event. With the documented topology it is instead the
 * steady state: every event delivered N times, every time, no crash involved.
 *
 * <p>The interleaving below is forced with latches rather than hoped for, so this
 * is a deterministic test, not a stress test: pod A is held inside
 * {@code dispatch()} — after its SELECT, before its UPDATE — while pod B runs a
 * full poll. That is exactly what two pods 5 seconds apart look like.
 */
class OutboxDispatcherClusterTest {

    /** In-memory stand-in for {@code core_domain_event}: id → row. */
    private final Map<String, DomainEventEntity> table = new LinkedHashMap<>();
    private DomainEventMapper mapper;

    @BeforeEach
    void setUp() {
        table.clear();
        table.put("evt-1", event("evt-1"));
        mapper = fakeMapper();
    }

    @Test
    @DisplayName("two pods polling the same outbox must not both dispatch the same event")
    void concurrentPods_dispatchEachEventOnce() throws Exception {
        List<String> dispatched = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch insideDispatch = new CountDownLatch(1);
        CountDownLatch letPodAFinish = new CountDownLatch(1);

        // Pod A blocks inside the sink, i.e. it has SELECTed but not yet UPDATEd.
        EventDispatchSink blockingSink = e -> {
            dispatched.add(e.getId());
            insideDispatch.countDown();
            try {
                letPodAFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        };
        EventDispatchSink plainSink = e -> dispatched.add(e.getId());

        FakeLock lock = new FakeLock();
        OutboxDispatcher podA = dispatcher(blockingSink, lock);
        OutboxDispatcher podB = dispatcher(plainSink, lock);

        AtomicReference<Throwable> podAError = new AtomicReference<>();
        Thread a = new Thread(() -> {
            try {
                podA.dispatchPending();
            } catch (Throwable t) {
                podAError.set(t);
            }
        }, "pod-A");
        a.start();

        assertThat(insideDispatch.await(5, TimeUnit.SECONDS))
                .as("pod A should have entered the sink")
                .isTrue();

        // Pod B's whole poll happens while pod A is mid-flight. Its SELECT sees
        // dispatch_state = 0 because pod A has not written the row back yet.
        podB.dispatchPending();

        letPodAFinish.countDown();
        a.join(5_000);
        assertThat(podAError.get()).isNull();

        assertThat(dispatched)
                .as("the same domain event was handed to the sink by both pods — with "
                        + "docs/deployment.md's pod×2~N topology every event is delivered N "
                        + "times on the happy path, which is not the crash-only duplication "
                        + "EventDispatchSink's contract describes")
                .containsExactly("evt-1");
    }

    @Test
    @DisplayName("a single pod still drains the outbox (the guard must not deadlock the common case)")
    void singlePod_stillDispatches() {
        List<String> dispatched = new ArrayList<>();
        FakeLock lock = new FakeLock();
        OutboxDispatcher pod = dispatcher(e -> dispatched.add(e.getId()), lock);

        pod.dispatchPending();
        assertThat(dispatched).containsExactly("evt-1");
        assertThat(table.get("evt-1").getDispatchState()).isEqualTo(1);

        // A second poll finds nothing left, and the lock was released rather than
        // held — otherwise the next tick on this same pod would be a no-op forever.
        pod.dispatchPending();
        assertThat(dispatched).containsExactly("evt-1");
        assertThat(lock.held).isFalse();
    }

    // ── plumbing ──────────────────────────────────────────────────────

    private OutboxDispatcher dispatcher(EventDispatchSink sink, JobLockService lock) {
        ObjectProvider<EventDispatchSink> provider = provider(sink);
        return new OutboxDispatcher(mapper, provider, lock, 200, 5, 60);
    }

    private static DomainEventEntity event(String id) {
        DomainEventEntity e = new DomainEventEntity();
        e.setId(id);
        e.setDispatchState(0);
        e.setDispatchAttempts(0);
        return e;
    }

    /**
     * Models the two statements the dispatcher issues. {@code selectPage} returns
     * the pending rows; {@code updateById} applies the patch. Nothing here claims
     * a row — faithfully, because the production SQL doesn't either.
     */
    @SuppressWarnings("unchecked")
    private DomainEventMapper fakeMapper() {
        DomainEventMapper m = mock(DomainEventMapper.class);
        when(m.selectPage(any(), any())).thenAnswer(inv -> {
            Page<DomainEventEntity> page = inv.getArgument(0);
            List<DomainEventEntity> pending;
            synchronized (table) {
                pending = table.values().stream()
                        .filter(r -> r.getDispatchState() != null && r.getDispatchState() == 0)
                        .sorted(Comparator.comparing(DomainEventEntity::getId))
                        .toList();
            }
            page.setRecords(pending);
            return page;
        });
        when(m.updateById(any(DomainEventEntity.class))).thenAnswer(inv -> {
            DomainEventEntity patch = inv.getArgument(0);
            synchronized (table) {
                DomainEventEntity row = table.get(patch.getId());
                if (row == null) return 0;
                if (patch.getDispatchState() != null) row.setDispatchState(patch.getDispatchState());
                if (patch.getDispatchAttempts() != null) row.setDispatchAttempts(patch.getDispatchAttempts());
                if (patch.getDispatchedAt() != null) row.setDispatchedAt(patch.getDispatchedAt());
            }
            return 1;
        });
        return m;
    }

    private static ObjectProvider<EventDispatchSink> provider(EventDispatchSink sink) {
        @SuppressWarnings("unchecked")
        ObjectProvider<EventDispatchSink> p = mock(ObjectProvider.class);
        when(p.getIfAvailable(any())).thenReturn(sink);
        return p;
    }

    /** Row-in-a-table lock, modelled as the single flag {@code core_job_lock} really is. */
    private static final class FakeLock extends JobLockService {
        private boolean held;

        private FakeLock() {
            super(null, new JobSchedulingConfig.NodeIdentity("test-node"));
        }

        @Override
        public synchronized boolean tryAcquire(String lockName, int maxRunSeconds) {
            if (held) return false;
            held = true;
            return true;
        }

        @Override
        public synchronized void release(String lockName) {
            held = false;
        }
    }
}
