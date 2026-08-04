package com.platform.business.demo.task.service;

import com.platform.business.demo.task.dto.TaskDto;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.business.demo.task.mapper.TaskMapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.infrastructure.event.EventPublisher;
import com.platform.core.infrastructure.security.rbac.DataScopeDecision;
import com.platform.core.infrastructure.security.rbac.DataScopeResolver;
import com.platform.system.dict.service.DictQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A dict option retired with {@code status=0} must be refused as NEW input while
 * still being carried forward on an existing row.
 *
 * <p>{@code DictAdminService.deleteItem} refuses to hard-delete any value the code
 * branches on or that business data references, and tells the operator to disable it
 * instead — so {@code status=0} is the only retirement mechanism a managed dict has.
 * The validator used to call {@code isValidValue}, which ignores status, so retiring
 * an option had no server-side effect at all: {@code useDict.options} stopped offering
 * it in fresh UI, but any client — including a browser tab holding a pre-disable dict
 * cache — could still write it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceDictGateTest {

    @Mock TaskMapper taskMapper;
    @Mock DataScopeResolver dataScopeResolver;
    @Mock ApplicationEventPublisher publisher;
    @Mock DictQueryService dictQueryService;
    @Mock EventPublisher events;

    private TaskService service;

    private static final int LIVE = 1;
    private static final int RETIRED = 3;

    @BeforeEach
    void setUp() {
        RequestContext.set("demo", "01ARZ3NDEKTSV4RRFFQ69G5FAV", "tester", Locale.JAPAN, "trace-1");
        service = new TaskService(taskMapper, dataScopeResolver, publisher, dictQueryService, events);
        when(dataScopeResolver.currentDecision()).thenReturn(DataScopeDecision.unrestricted("01ARZ3NDEKTSV4RRFFQ69G5FAV"));
        // updateById now goes through ConcurrentEdit.requireApplied(...): 0 affected rows
        // means a concurrent editor advanced the @Version column. Mockito defaults an int
        // return to 0, so every mocked update would look like a lost update.
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);
        // Both dicts: LIVE is selectable, RETIRED is a legal value but not selectable.
        when(dictQueryService.isValidValue(anyString(), any())).thenAnswer(inv -> {
            Object v = inv.getArgument(1);
            return v != null && (LIVE == asInt(v) || RETIRED == asInt(v) || 2 == asInt(v));
        });
        when(dictQueryService.isSelectableValue(anyString(), any())).thenAnswer(inv -> {
            Object v = inv.getArgument(1);
            return v != null && RETIRED != asInt(v) && (LIVE == asInt(v) || 2 == asInt(v));
        });
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : Integer.MIN_VALUE;
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static TaskDto.CreateRequest create(int status, int priority) {
        return new TaskDto.CreateRequest("01ARZ3NDEKTSV4RRFFQ69G5FAV", "t", null,
                status, priority, null, null);
    }

    private TaskEntity row(int status, int priority) {
        TaskEntity t = new TaskEntity();
        t.setId("01ARZ3NDEKTSV4RRFFQ69G5FAW");
        t.setMark(1);
        t.setDeptId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        t.setStatus(status);
        t.setPriority(priority);
        when(taskMapper.selectById(t.getId())).thenReturn(t);
        return t;
    }

    @Test
    void create_refusesARetiredStatus() {
        assertThatThrownBy(() -> service.create(create(RETIRED, LIVE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.dict.invalidValue");

        verify(taskMapper, never()).insert(any(TaskEntity.class));
    }

    @Test
    void create_refusesARetiredPriority() {
        assertThatThrownBy(() -> service.create(create(LIVE, RETIRED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.dict.invalidValue");

        verify(taskMapper, never()).insert(any(TaskEntity.class));
    }

    @Test
    void create_acceptsLiveValues() {
        service.create(create(LIVE, 2));

        verify(taskMapper).insert(any(TaskEntity.class));
    }

    @Test
    void update_refusesMovingAnExistingRowToARetiredStatus() {
        row(LIVE, LIVE);

        assertThatThrownBy(() -> service.update("01ARZ3NDEKTSV4RRFFQ69G5FAW",
                new TaskDto.UpdateRequest(null, null, null, RETIRED, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.dict.invalidValue");

        verify(taskMapper, never()).updateById(any(TaskEntity.class));
    }

    @Test
    void update_carriesForwardARetiredValueThatIsAlreadyStored() {
        // The row already sits on the retired value (it was live when set). Editing
        // any other field re-sends it unchanged — that must NOT fail, or an old row
        // becomes uneditable the moment ops retires the option it happens to hold.
        TaskEntity t = row(RETIRED, RETIRED);

        service.update(t.getId(),
                new TaskDto.UpdateRequest(null, "new title", null, RETIRED, RETIRED, null, null));

        verify(taskMapper).updateById(t);
        assertThat(t.getTitle()).isEqualTo("new title");
        assertThat(t.getStatus()).isEqualTo(RETIRED);
        assertThat(t.getPriority()).isEqualTo(RETIRED);
    }

    @Test
    void update_stillAllowsMovingOffARetiredValueToALiveOne() {
        TaskEntity t = row(RETIRED, RETIRED);

        service.update(t.getId(),
                new TaskDto.UpdateRequest(null, null, null, LIVE, 2, null, null));

        verify(taskMapper).updateById(t);
        assertThat(t.getStatus()).isEqualTo(LIVE);
        assertThat(t.getPriority()).isEqualTo(2);
    }

    @Test
    void update_leavingDictFieldsOutNeverValidates() {
        TaskEntity t = row(RETIRED, RETIRED);

        service.update(t.getId(),
                new TaskDto.UpdateRequest(null, "title only", null, null, null, null, null));

        verify(dictQueryService, never()).isSelectableValue(eq("task_status"), any());
        verify(taskMapper).updateById(t);
    }
}
