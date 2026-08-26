package com.platform.business.demo.startup;

import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.business.demo.task.mapper.TaskMapper;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.mapper.UserRoleMapper;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting a demo row must not brick the seeder on the next boot.
 *
 * <p>Every row DemoSeeder plants carries a FIXED id (the five demo users, the
 * fifteen {@code DEMOTASK…} tasks). It probed for them with {@code selectById},
 * but {@code mark} is {@code @TableLogic}, so MyBatis-Plus appends
 * {@code AND mark = 1} and a row soft-deleted through the UI — the Task page has
 * a Delete button, and deleting a demo task is the first thing anyone tries —
 * reads back as null, indistinguishable from "never seeded". The seeder then
 * re-inserted an id the table still holds and the PRIMARY KEY rejected it.
 *
 * <p>{@code seed()} wraps the whole run in
 * {@code catch (Exception e) { log.warn("DemoSeeder: skipped — …") }}, so that
 * surfaced as one WARN line and everything after the failing row was abandoned —
 * including {@code syncUsersToKeycloak()}, which runs last. In the default dev
 * (oidc) mode that is exactly what leaves the demo users unable to sign in at
 * all: no Keycloak account, and not super-admins, so no break-glass either.
 *
 * <p>The seeder's own contract is "re-create whatever is missing on every boot",
 * and a soft-deleted row is missing everywhere the app looks — so the fix
 * revives it rather than skipping, which is also what {@code JobSeeder} does for
 * the same situation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoSeederRevivalTest {

    private static final String USER_ID = "00000000000000000000USER11";   // tanaka_taro
    private static final String TASK_ID = "DEMOTASK0000000000HQKICKOF";   // 本社 キックオフ会議

    @Mock UserMapper userMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock TaskMapper taskMapper;
    @Mock PasswordEncoder encoder;

    @SuppressWarnings("unchecked")
    private DemoSeeder seeder() {
        ObjectProvider<KeycloakUserService> kc = (ObjectProvider<KeycloakUserService>) mock(ObjectProvider.class);
        when(kc.getIfAvailable()).thenReturn(null);
        when(encoder.encode(anyString())).thenReturn("$2a$hash");
        return new DemoSeeder(userMapper, userRoleMapper, taskMapper, encoder, kc);
    }

    /** Nothing exists yet → the seeder plants every row it owns. */
    @Test
    void aVirginDatabaseGetsSeeded() {
        when(userMapper.findMarkById(anyString())).thenReturn(null);
        when(taskMapper.findMarkById(anyString())).thenReturn(null);

        seeder().seed();

        verify(userMapper, atLeastOnce()).insert(any(UserEntity.class));
        verify(taskMapper, atLeastOnce()).insert(any(TaskEntity.class));
        verify(userMapper, never()).reviveById(anyString(), any());
        verify(taskMapper, never()).reviveById(anyString(), any());
    }

    /** Everything is already live → the seeder writes nothing at all. */
    @Test
    void aFullySeededDatabaseIsLeftAlone() {
        when(userMapper.findMarkById(anyString())).thenReturn(1);
        when(taskMapper.findMarkById(anyString())).thenReturn(1);

        seeder().seed();

        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(taskMapper, never()).insert(any(TaskEntity.class));
        verify(userMapper, never()).reviveById(anyString(), any());
        verify(taskMapper, never()).reviveById(anyString(), any());
    }

    /**
     * The row is soft-deleted → revive it. Re-inserting is what blew up on the
     * primary key, so `insert` must not be reached for that id.
     */
    @Test
    void aSoftDeletedRowIsRevivedRatherThanReinserted() {
        when(userMapper.findMarkById(anyString())).thenReturn(1);
        when(userMapper.findMarkById(USER_ID)).thenReturn(0);
        when(userMapper.reviveById(eq(USER_ID), any())).thenReturn(1);
        when(taskMapper.findMarkById(anyString())).thenReturn(1);
        when(taskMapper.findMarkById(TASK_ID)).thenReturn(0);
        when(taskMapper.reviveById(eq(TASK_ID), any())).thenReturn(1);

        seeder().seed();

        verify(userMapper).reviveById(eq(USER_ID), any());
        verify(taskMapper).reviveById(eq(TASK_ID), any());
        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(taskMapper, never()).insert(any(TaskEntity.class));
    }

    /**
     * The whole point: one deleted task must not stop the run before
     * syncUsersToKeycloak(), which is the last step and the one that makes the
     * demo users able to sign in at all in oidc mode.
     */
    @Test
    void oneDeletedTaskDoesNotAbandonTheRestOfTheRun() {
        when(userMapper.findMarkById(anyString())).thenReturn(1);
        when(taskMapper.findMarkById(anyString())).thenReturn(null);
        when(taskMapper.findMarkById(TASK_ID)).thenReturn(0);
        when(taskMapper.reviveById(eq(TASK_ID), any())).thenReturn(1);

        seeder().seed();

        // All FIFTEEN tasks were reached: the deleted one revived, the other
        // fourteen inserted. Before the fix the run stopped at the deleted one.
        verify(taskMapper).reviveById(eq(TASK_ID), any());
        verify(taskMapper, times(14)).insert(any(TaskEntity.class));
    }
}
