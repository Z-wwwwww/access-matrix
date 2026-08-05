package com.platform.business.demo.task.service;

import com.platform.business.demo.task.TaskStatus;
import com.platform.business.demo.task.dto.TaskDto;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.business.demo.task.mapper.TaskMapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.notification.NotificationResolvedEvent;
import com.platform.core.infrastructure.event.EventPublisher;
import com.platform.core.infrastructure.security.rbac.DataScopeDecision;
import com.platform.core.infrastructure.security.rbac.DataScopeResolver;
import com.platform.system.dict.service.DictQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An {@code action}-kind notification is a to-do: {@code notifyAssignee} raises one
 * when a task gets an assignee, and it stays unread — lighting the assignee's bell —
 * until something declares the underlying decision finished. That "something" is a
 * {@link NotificationResolvedEvent}, which {@code NotificationEventListener.onResolved}
 * turns into {@code markReadByBiz} + a fresh unread push.
 *
 * <p>{@code update} raised it when the status moved to 完了 / 取消. {@code delete} —
 * which is strictly more final — did not, so deleting an assigned task left its
 * assignee with a permanently unread "対応が必要" item that could never be resolved by
 * any subsequent action: the bell keeps the count, and opening the item lands on a
 * {@code bizId} whose row is now {@code mark = 0}, which {@code loadVisibleOr404}
 * answers with NOT_FOUND.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceNotificationResolveTest {

    @Mock TaskMapper taskMapper;
    @Mock DataScopeResolver dataScopeResolver;
    @Mock ApplicationEventPublisher publisher;
    @Mock DictQueryService dictQueryService;
    @Mock EventPublisher events;

    private static final String ME = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
    private static final String TASK_ID = "01ARZ3NDEKTSV4RRFFQ69G5FBB";

    private TaskService service;

    @BeforeEach
    void setUp() {
        RequestContext.set("demo", ME, "tester", Locale.JAPAN, "trace-1");
        service = new TaskService(taskMapper, dataScopeResolver, publisher, dictQueryService, events);
        when(dataScopeResolver.currentDecision()).thenReturn(DataScopeDecision.unrestricted(ME));
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);
        when(dictQueryService.isValidValue(anyString(), any())).thenReturn(true);
        when(dictQueryService.isSelectableValue(anyString(), any())).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(existingTask());
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private static TaskEntity existingTask() {
        TaskEntity t = new TaskEntity();
        t.setId(TASK_ID);
        t.setMark(1);
        t.setTitle("既存タスク");
        t.setStatus(TaskStatus.TODO.code());
        t.setPriority(1);
        t.setDeptId("01ARZ3NDEKTSV4RRFFQ69G5FDD");
        t.setAssigneeUserId("01ARZ3NDEKTSV4RRFFQ69G5FCC");
        t.setCreateUser(ME);
        return t;
    }

    private NotificationResolvedEvent capturedResolvedEvent() {
        // atLeast(0), not atLeastOnce(): "no event at all" is a legitimate outcome
        // (an ordinary edit publishes nothing) and must read as "not resolved"
        // rather than blowing up the capture.
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(NotificationResolvedEvent.class::isInstance)
                .map(NotificationResolvedEvent.class::cast)
                .findFirst()
                .orElse(null);
    }

    @Test
    void deleting_a_task_resolves_its_outstanding_action_notifications() {
        service.delete(TASK_ID);

        NotificationResolvedEvent e = capturedResolvedEvent();
        assertThat(e)
                .as("delete left the assignee's action notification unread forever")
                .isNotNull();
        assertThat(e.bizType()).isEqualTo("demo_task");
        assertThat(e.bizId()).isEqualTo(TASK_ID);
        assertThat(e.tenantId()).isEqualTo("demo");
    }

    @Test
    void completing_a_task_still_resolves_them() {
        // The pre-existing behaviour delete was missing — pinned so the two paths
        // can't drift apart again.
        service.update(TASK_ID, new TaskDto.UpdateRequest(
                null, null, null, TaskStatus.DONE.code(), null, null, null));

        NotificationResolvedEvent e = capturedResolvedEvent();
        assertThat(e).isNotNull();
        assertThat(e.bizId()).isEqualTo(TASK_ID);
    }

    @Test
    void an_ordinary_edit_does_not_resolve_them() {
        // Still open → the assignee's to-do must stay on the bell.
        service.update(TASK_ID, new TaskDto.UpdateRequest(
                null, "タイトル変更", null, null, null, null, null));

        assertThat(capturedResolvedEvent()).isNull();
    }
}
