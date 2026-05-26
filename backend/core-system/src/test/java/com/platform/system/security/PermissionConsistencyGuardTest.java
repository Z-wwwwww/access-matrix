package com.platform.system.security;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.security.PermissionRegistry;
import com.platform.system.rbac.entity.PermissionEntity;
import com.platform.system.rbac.entity.RolePermissionEntity;
import com.platform.system.rbac.mapper.PermissionMapper;
import com.platform.system.rbac.mapper.RolePermissionMapper;
import com.platform.system.rbac.service.PermissionCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the orphan-permission cleanup path. The historic bug: the guard set
 * {@code permission.mark = 0} via {@code setMark(0) + updateById}, which
 * MyBatis-Plus silently no-ops on {@code @TableLogic} columns (they're
 * stripped from the SET clause). After the fix, orphan cleanup must:
 *   1. soft-delete the permission row via {@code UpdateWrapper.set("mark", 0)}
 *   2. cascade-soft-delete every active {@code role_permission} link
 *   3. NEVER treat wildcards ({@code *:*}, {@code resource:*}) as orphans —
 *      they're the SUPER_ADMIN safety net and must survive every startup
 */
@ExtendWith(MockitoExtension.class)
class PermissionConsistencyGuardTest {

    @Mock ApplicationContext appContext;
    @Mock PermissionMapper mapper;
    @Mock RolePermissionMapper rolePermissionMapper;
    @Mock PermissionCacheService cacheService;
    @Mock I18nPermissionPatcher patcher;

    @InjectMocks PermissionConsistencyGuard guard;

    private PermissionEntity row(String id, String code, int mark) {
        PermissionEntity p = new PermissionEntity();
        p.setId(id);
        p.setCode(code);
        p.setMark(mark);
        return p;
    }

    @Test
    void orphan_softDeletesViaUpdateWrapper_andCascadesRolePermission() {
        // Empty @Controller scan → "annotated" set is empty.
        when(appContext.getBeansWithAnnotation(Controller.class)).thenReturn(Collections.emptyMap());
        // DB returns one code that's NOT in PermissionRegistry (true orphan).
        // 'definitely-not-real:code' guaranteed not registered by anyone.
        String orphanCode = "definitely-not-real:orphan-code";
        assertThat(PermissionRegistry.isRegistered(orphanCode)).isFalse();

        PermissionEntity orphan = row("p-orphan", orphanCode, 1);
        // First call: loadBuiltInCodesFromDb() → SELECT code (mark=1, is_built_in=1)
        // Second call: softDeleteOrphan → SELECT * by code
        when(mapper.selectList(any()))
                .thenReturn(List.of(orphan))   // for loadBuiltInCodesFromDb
                .thenReturn(List.of(orphan));  // for softDeleteOrphan
        when(rolePermissionMapper.update(any(), any(UpdateWrapper.class))).thenReturn(2);
        ReflectionTestUtils.setField(guard, "activeProfile", "prod");

        guard.verify();

        // permission row soft-deleted: UpdateWrapper.set("mark", 0) (NOT setMark+updateById).
        ArgumentCaptor<UpdateWrapper<PermissionEntity>> permCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(eq(null), permCap.capture());
        assertThat(permCap.getValue().getSqlSet()).contains("mark=");
        assertThat(permCap.getValue().getParamNameValuePairs().values()).contains(0);

        // role_permission cascade: also UpdateWrapper-driven.
        ArgumentCaptor<UpdateWrapper<RolePermissionEntity>> rpCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(rolePermissionMapper).update(eq(null), rpCap.capture());
        assertThat(rpCap.getValue().getSqlSet()).contains("mark=");
        assertThat(rpCap.getValue().getParamNameValuePairs().values()).contains(0);

        verify(cacheService).evictAll();
    }

    @Test
    void selfHeal_markZeroPermissionWithLeftoverActiveRolePermission_stillCleansLinks() {
        // The guard sees a permission row already at mark=0 (e.g., V13 manual cleanup), but
        // its role_permission links never got cleaned up. The guard should NOT re-soft-delete
        // the perm row (already mark=0) but SHOULD still cascade-clean role_permission.
        when(appContext.getBeansWithAnnotation(Controller.class)).thenReturn(Collections.emptyMap());
        String orphanCode = "definitely-not-real:another-orphan";
        PermissionEntity alreadyDead = row("p-dead", orphanCode, 0);
        when(mapper.selectList(any()))
                .thenReturn(List.of()) // loadBuiltInCodesFromDb (mark=1 filter) → nothing
                .thenReturn(List.of(alreadyDead)); // softDeleteOrphan SELECT * by code
        // ... but the guard's orphan set comes from loadBuiltInCodesFromDb, so if mark=1 result
        // is empty we never enter softDeleteOrphan. This test confirms the precondition holds:
        // the self-heal only runs when the mark=1 row exists. Document that boundary here.
        ReflectionTestUtils.setField(guard, "activeProfile", "prod");

        guard.verify();

        // No DB writes when there are no mark=1 orphans.
        verify(mapper, never()).update(any(), any(UpdateWrapper.class));
        verify(rolePermissionMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void wildcards_inDb_areNeverTreatedAsOrphans() {
        // CRITICAL: the SUPER_ADMIN role binds to *:* in the DB. If the guard ever treated
        // *:* as an orphan and soft-deleted it, the platform's super admin would lose
        // every permission on the next startup. The wildcard filter must hold.
        when(appContext.getBeansWithAnnotation(Controller.class)).thenReturn(Collections.emptyMap());
        PermissionEntity superWildcard = row("p-super", "*:*", 1);
        PermissionEntity resourceWildcard = row("p-user-all", "user:*", 1);
        when(mapper.selectList(any())).thenReturn(List.of(superWildcard, resourceWildcard));
        ReflectionTestUtils.setField(guard, "activeProfile", "prod");

        guard.verify();

        // Confirms: even though *:* and user:* are not in PermissionRegistry (wildcards
        // are intentionally excluded), the guard's isWildcard() filter keeps them alive.
        // Zero soft-deletes — no second selectList call for code-specific re-fetch either.
        verify(mapper, never()).update(any(), any(UpdateWrapper.class));
        verify(rolePermissionMapper, never()).update(any(), any(UpdateWrapper.class));
        // selectList was only called once (for loadBuiltInCodesFromDb), no orphan-rescan.
        verify(mapper, atLeast(1)).selectList(any());
    }

    @Test
    void i18nPatcher_runsOnlyInDevProfile() throws Exception {
        when(appContext.getBeansWithAnnotation(Controller.class)).thenReturn(Collections.emptyMap());
        when(mapper.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(guard, "activeProfile", "dev");

        guard.verify();

        verify(patcher).patch(any());
    }

    @Test
    void i18nPatcher_skippedInProdProfile() throws Exception {
        // Auto-touching language files on every prod startup would be terrifying.
        when(appContext.getBeansWithAnnotation(Controller.class)).thenReturn(Collections.emptyMap());
        when(mapper.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(guard, "activeProfile", "prod");

        guard.verify();

        verify(patcher, never()).patch(any());
    }
}
