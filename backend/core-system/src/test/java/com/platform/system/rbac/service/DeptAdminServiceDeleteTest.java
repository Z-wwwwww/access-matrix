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
        when(deptMapper.findSubtreeIds(eq("/d1"), eq("default")))
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
    void deleteWithForce_passesTenantIdToFindSubtreeIds() {
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
        when(deptMapper.findSubtreeIds(anyString(), anyString())).thenReturn(List.of("d1"));

        service.delete("d1", true);

        verify(deptMapper).findSubtreeIds("/d1", "acme");
    }
}
