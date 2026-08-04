package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.DeptEntity;
import com.platform.system.rbac.entity.RoleDeptEntity;
import com.platform.system.rbac.mapper.DeptMapper;
import com.platform.system.rbac.mapper.RoleDeptMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins behaviour of dept-delete:
 *   - IN_USE check must count children + users + role_dept references
 *     (the missing role_dept count was the silent-bug we hit on 测试公司总部)
 *   - non-force path: simple soft delete via UpdateWrapper (@TableLogic safe)
 *   - force path: subtree-wide cascade — descendant depts soft-deleted,
 *     users in subtree get dept_id=null, role_dept references cleared.
 */
@ExtendWith(MockitoExtension.class)
class DeptAdminServiceDeleteTest {

    @Mock DeptMapper deptMapper;
    @Mock RoleDeptMapper roleDeptMapper;
    @Mock UserMapper userMapper;
    @Mock PermissionCacheService cacheService;

    @InjectMocks DeptAdminService service;

    @BeforeEach
    void seedTenant() {
        RequestContext.set("default", "tester", "tester", Locale.JAPAN, "trace-1");
        // updateById now goes through ConcurrentEdit.requireApplied(...): 0 affected rows
        // means a concurrent editor advanced the @Version column. Mockito defaults an int
        // return to 0, so every mocked update would look like a lost update.
        org.mockito.Mockito.lenient()
                .when(deptMapper.updateById(org.mockito.ArgumentMatchers.any(DeptEntity.class)))
                .thenReturn(1);
    }

    @AfterEach
    void clearTenant() {
        RequestContext.clear();
    }

    private DeptEntity dept(String id, String path) {
        DeptEntity d = new DeptEntity();
        d.setId(id);
        d.setPath(path);
        d.setName("dept-" + id);
        d.setMark(1);
        return d;
    }

    // ─── disable guard (update status 1→0) ─────────────────────────────
    // Disabling a dept silently shrinks every SCOPE_CUSTOM role referencing
    // it (findSubtreeIds filters status=1), and the role-edit tree hides
    // disabled depts so the binding turns into an invisible ghost. The
    // update path therefore runs the same IN_USE + force handshake as delete.

    @Test
    void disableWithoutForce_throwsInUseWhenRoleRefsExist() {
        DeptEntity d = dept("d1", "/d1");
        d.setStatus(1);
        when(deptMapper.selectById("d1")).thenReturn(d);
        when(roleDeptMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.update("d1",
                new com.platform.system.rbac.dto.DeptAdminDto.UpdateRequest(null, null, null, null, 0), false))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.IN_USE);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> detail = (Map<String, Object>) ex.detail();
                    assertThat(detail).containsEntry("roles", 2L);
                });

        verify(deptMapper, never()).updateById(any(DeptEntity.class));
    }

    @Test
    void disableWithForce_proceeds() {
        DeptEntity d = dept("d1", "/d1");
        d.setStatus(1);
        when(deptMapper.selectById("d1")).thenReturn(d);

        service.update("d1",
                new com.platform.system.rbac.dto.DeptAdminDto.UpdateRequest(null, null, null, null, 0), true);

        // force skips the ref count entirely and writes the new status.
        verify(roleDeptMapper, never()).selectCount(any());
        assertThat(d.getStatus()).isEqualTo(0);
        verify(deptMapper).updateById(d);
    }

    @Test
    void enable_neverChecksRoleRefs() {
        // Only the disable direction can shrink a CUSTOM scope — re-enabling
        // (or re-asserting enabled) must not trip the guard.
        DeptEntity d = dept("d1", "/d1");
        d.setStatus(0);
        when(deptMapper.selectById("d1")).thenReturn(d);

        service.update("d1",
                new com.platform.system.rbac.dto.DeptAdminDto.UpdateRequest(null, null, null, null, 1), false);

        verify(roleDeptMapper, never()).selectCount(any());
        assertThat(d.getStatus()).isEqualTo(1);
    }

    @Test
    void deleteWithoutForce_throwsInUseWhenRoleRefsExist_evenIfNoChildrenNoUsers() {
        // Specific regression: role_dept refs alone (no children, no direct user.dept_id)
        // used to slip past the in-use check and silently shrink the referencing role's
        // CUSTOM scope.
        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));
        when(deptMapper.selectCount(any())).thenReturn(0L); // children
        when(userMapper.selectCount(any())).thenReturn(0L); // users
        when(roleDeptMapper.selectCount(any())).thenReturn(2L); // role refs

        assertThatThrownBy(() -> service.delete("d1", false))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.IN_USE);
                    assertThat(ex.getMessage()).contains("2 role");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> d = (Map<String, Object>) ex.detail();
                    assertThat(d).containsEntry("children", 0L)
                                 .containsEntry("users", 0L)
                                 .containsEntry("roles", 2L);
                });

        verify(deptMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void deleteWithoutForce_inUseDetailIncludesAllThreeCounts() {
        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));
        when(deptMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectCount(any())).thenReturn(5L);
        when(roleDeptMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete("d1", false))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.IN_USE);
                    assertThat(ex.getMessage())
                            .contains("1 sub-department")
                            .contains("5 user")
                            .contains("2 role");
                });
    }

    @Test
    void deleteWithoutForce_noDependencies_softDeletesViaUpdateWrapper() {
        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleDeptMapper.selectCount(any())).thenReturn(0L);

        service.delete("d1", false);

        // The dept's own soft-delete: UpdateWrapper.set("mark", 0) — NOT setMark+updateById.
        // MP wraps the literal value into ew.paramNameValuePairs, so getSqlSet() reads
        // "mark=#{ew.paramNameValuePairs.MPGENVAL1}" — assert both the column appears in
        // the SET clause AND that the parameter map carries the literal 0.
        ArgumentCaptor<UpdateWrapper<DeptEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(deptMapper).update(eq(null), cap.capture());
        assertThat(cap.getValue().getSqlSet()).contains("mark=");
        assertThat(cap.getValue().getParamNameValuePairs().values()).contains(0);

        verify(roleDeptMapper).update(eq(null), any(UpdateWrapper.class));
        verify(cacheService).evictAllDepts();
    }

    @Test
    void deleteWithForce_subtreeCascade_nullsUserDeptIdAndClearsRoleDept() {
        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));
        when(deptMapper.selectCount(any())).thenReturn(1L); // children
        when(userMapper.selectCount(any())).thenReturn(2L); // users
        when(roleDeptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.findSubtreeIdsAnyStatus(eq("/d1"), eq("default")))
                .thenReturn(List.of("d1", "d1-1", "d1-2"));

        service.delete("d1", true);

        // user_id IN (subtree) → set dept_id=NULL
        ArgumentCaptor<UpdateWrapper<UserEntity>> userCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(eq(null), userCap.capture());
        // We're not asserting exact SQL here (varies with MP version), just that the call happened.
        assertThat(userCap.getValue().getSqlSet()).contains("dept_id");

        // role_dept references for all subtree dept ids → mark=0
        ArgumentCaptor<UpdateWrapper<RoleDeptEntity>> rdCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(roleDeptMapper).update(eq(null), rdCap.capture());
        assertThat(rdCap.getValue().getSqlSet()).contains("mark=");
        assertThat(rdCap.getValue().getParamNameValuePairs().values()).contains(0);

        // All subtree depts soft-deleted
        verify(deptMapper, atLeastOnce()).update(eq(null), any(UpdateWrapper.class));
        verify(cacheService).evictAllDepts();
    }

    @Test
    void deleteWithForce_passesTenantIdToTheSubtreeQuery() {
        // Multi-tenant defense: findSubtreeIds must receive the current tenant,
        // not just the path, else a tenant force-deleting their own dept could
        // pick up another tenant's subtree (shouldn't happen due to ULID uniqueness,
        // but the explicit tenant_id filter is mandatory per repo convention).
        RequestContext.clear();
        RequestContext.set("acme", "tester", "tester", Locale.JAPAN, "trace-1");

        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));
        when(deptMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleDeptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.findSubtreeIdsAnyStatus(anyString(), anyString())).thenReturn(List.of("d1"));

        service.delete("d1", true);

        verify(deptMapper).findSubtreeIdsAnyStatus("/d1", "acme");
    }

    // ─── status-filter parity: pre-check set == cascade set ────────────────
    //
    // The IN_USE pre-check counts children with NO status filter, so a DISABLED
    // child makes delete() demand force=true. The cascade must then actually
    // include that child. It used to call findSubtreeIds, which filters
    // status=1 — verified against the real DB that the disabled child survived
    // (mark=1) with parent_id pointing at the just-soft-deleted parent, its
    // users' dept_id untouched and its role_dept bindings still live. The old
    // "empty → fall back to self" guard only caught a fully empty list, never a
    // partial one, which is exactly what a disabled child produces.

    @Test
    void deleteWithForce_usesTheAnyStatusSubtree_soADisabledChildIsNotOrphaned() {
        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));
        when(deptMapper.selectCount(any())).thenReturn(1L);   // the disabled child
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleDeptMapper.selectCount(any())).thenReturn(0L);
        // status=1 view would return only the parent; any-status returns both.
        when(deptMapper.findSubtreeIdsAnyStatus("/d1", "default"))
                .thenReturn(List.of("d1", "disabled-child"));

        service.delete("d1", true);

        // The status-filtered query must not be consulted for a tree mutation.
        verify(deptMapper, never()).findSubtreeIds(anyString(), anyString());

        // Both ids reach the dept soft-delete, the user un-assign and the role_dept clear.
        ArgumentCaptor<UpdateWrapper<DeptEntity>> deptCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(deptMapper).update(eq(null), deptCap.capture());
        assertThat(inValuesOf(deptCap.getValue())).contains("d1", "disabled-child");

        ArgumentCaptor<UpdateWrapper<UserEntity>> userCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(eq(null), userCap.capture());
        assertThat(inValuesOf(userCap.getValue())).contains("d1", "disabled-child");

        ArgumentCaptor<UpdateWrapper<RoleDeptEntity>> rdCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(roleDeptMapper).update(eq(null), rdCap.capture());
        assertThat(inValuesOf(rdCap.getValue())).contains("d1", "disabled-child");
    }

    /**
     * The values MP bound into an {@code IN (...)} clause. MP fills
     * {@code paramNameValuePairs} lazily: the SET values land there when the
     * wrapper is built, but the WHERE values only when the segment is
     * materialised — so {@code getSqlSegment()} has to be read first.
     */
    private static java.util.Collection<Object> inValuesOf(UpdateWrapper<?> w) {
        assertThat(w.getSqlSegment()).contains("IN (");
        return w.getParamNameValuePairs().values();
    }

    @Test
    void reparent_toADisabledDescendant_isRejected() {
        // Same root cause on the reparent path: the cycle check walked the
        // status=1 subtree, so a disabled descendant was invisible and passed —
        // while require() accepts it (it only asserts mark=1). Verified against
        // the real DB that this produced a genuine parent_id cycle (d1→child→d1)
        // and left d1's path containing its own id.
        DeptEntity d = dept("d1", "/d1");
        d.setStatus(1);
        when(deptMapper.selectById("d1")).thenReturn(d);
        when(deptMapper.findSubtreeIdsAnyStatus("/d1", "default"))
                .thenReturn(List.of("d1", "disabled-child"));

        assertThatThrownBy(() -> service.update("d1",
                new com.platform.system.rbac.dto.DeptAdminDto.UpdateRequest(
                        "disabled-child", null, null, null, null), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("descendant");

        verify(deptMapper, never()).updateById(any(DeptEntity.class));
        verify(deptMapper, never()).findSubtreeIds(anyString(), anyString());
    }

    // ─── reparent must re-root the descendants too ──────────────────────────
    //
    // path/level are DERIVED from the parent chain. Rewriting them only on the moved
    // node left the tree self-inconsistent: parent_id (what the admin UI renders) stayed
    // correct while path — what the data-scope resolution keys on — still described the
    // old position. Verified against the real DB that moving a dept with a child made
    // DEPT_AND_SUB for the moved node return only itself (its manager lost the child's
    // data) while the child stayed in the old ancestor's subtree; and that the cascading
    // UPDATE restores the invariant child.path = parent.path||'/'||id for every edge,
    // multi-level tails included. Was deferred to a "rebuild paths" command never built.

    @Test
    void reparent_reRootsDescendants_withTheCorrectPrefixAndLevelDelta() {
        DeptEntity d = dept("d1", "/root/d1");
        d.setStatus(1);
        d.setLevel(2);
        DeptEntity newParent = dept("d9", "/root/branch/d9");
        newParent.setLevel(3);
        when(deptMapper.selectById("d1")).thenReturn(d);
        when(deptMapper.selectById("d9")).thenReturn(newParent);
        when(deptMapper.findSubtreeIdsAnyStatus("/root/d1", "default")).thenReturn(List.of("d1"));

        service.update("d1", new com.platform.system.rbac.dto.DeptAdminDto.UpdateRequest(
                "d9", null, null, null, null), false);

        // oldPath, its length (so substring keeps each descendant's own tail), the new
        // path, the level delta (4 - 2), and the tenant.
        verify(deptMapper).reRootDescendants("/root/d1", "/root/d1".length(),
                "/root/branch/d9/d1", 2, "default");
        assertThat(d.getPath()).isEqualTo("/root/branch/d9/d1");
        assertThat(d.getLevel()).isEqualTo(4);
    }

    @Test
    void reparent_isTheOnlyThingThatTriggersTheCascade() {
        // Renaming / re-sorting must not touch descendants.
        DeptEntity d = dept("d1", "/root/d1");
        d.setStatus(1);
        d.setLevel(2);
        when(deptMapper.selectById("d1")).thenReturn(d);

        service.update("d1", new com.platform.system.rbac.dto.DeptAdminDto.UpdateRequest(
                null, "new name", 5, null, null), false);

        verify(deptMapper, never()).reRootDescendants(anyString(), anyInt(), anyString(), anyInt(), anyString());
        assertThat(d.getName()).isEqualTo("new name");
    }

    @Test
    void reparent_toTheSameParent_isNotTreatedAsAMove() {
        DeptEntity d = dept("d1", "/root/d1");
        d.setStatus(1);
        d.setLevel(2);
        d.setParentId("root");
        when(deptMapper.selectById("d1")).thenReturn(d);

        service.update("d1", new com.platform.system.rbac.dto.DeptAdminDto.UpdateRequest(
                "root", null, null, null, null), false);

        verify(deptMapper, never()).reRootDescendants(anyString(), anyInt(), anyString(), anyInt(), anyString());
        assertThat(d.getPath()).isEqualTo("/root/d1");
    }

    @Test
    void reparent_toAnUnrelatedDept_isStillAllowed() {
        // The widened check must not start rejecting legitimate reparents.
        DeptEntity d = dept("d1", "/d1");
        d.setStatus(1);
        DeptEntity newParent = dept("d9", "/d9");
        newParent.setLevel(1);
        when(deptMapper.selectById("d1")).thenReturn(d);
        when(deptMapper.selectById("d9")).thenReturn(newParent);
        when(deptMapper.findSubtreeIdsAnyStatus("/d1", "default")).thenReturn(List.of("d1"));

        service.update("d1", new com.platform.system.rbac.dto.DeptAdminDto.UpdateRequest(
                "d9", null, null, null, null), false);

        assertThat(d.getParentId()).isEqualTo("d9");
        assertThat(d.getPath()).isEqualTo("/d9/d1");
        verify(deptMapper).updateById(d);
    }
}
