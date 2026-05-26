package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.entity.RoleDeptEntity;
import com.platform.system.rbac.entity.RoleMenuEntity;
import com.platform.system.rbac.entity.RolePermissionEntity;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.DeptMapper;
import com.platform.system.rbac.mapper.MenuMapper;
import com.platform.system.rbac.mapper.PermissionMapper;
import com.platform.system.rbac.mapper.RoleDeptMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.mapper.RoleMenuMapper;
import com.platform.system.rbac.mapper.RolePermissionMapper;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins behaviour of the role-delete path that's been historically broken:
 *
 *   1. soft-deleting via {@code setMark(0) + updateById} silently no-ops on
 *      {@code @TableLogic} fields. Service must use {@code UpdateWrapper.set}.
 *   2. user_role cleanup was missing — old code left zombie links that
 *      resurrected when a role was un-deleted manually.
 *   3. {@code evictRole} must run BEFORE the user_role soft delete, otherwise
 *      it can't find the affected users.
 *   4. IN_USE with structured detail when caller didn't pass force=true.
 */
@ExtendWith(MockitoExtension.class)
class RoleAdminServiceDeleteTest {

    @Mock RoleMapper roleMapper;
    @Mock RolePermissionMapper rolePermissionMapper;
    @Mock RoleMenuMapper roleMenuMapper;
    @Mock RoleDeptMapper roleDeptMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock PermissionMapper permissionMapper;
    @Mock MenuMapper menuMapper;
    @Mock DeptMapper deptMapper;
    @Mock PermissionCacheService cacheService;

    @InjectMocks RoleAdminService service;

    @BeforeEach
    void seedTenant() {
        RequestContext.set("default", "tester", "tester", Locale.JAPAN, "trace-1");
    }

    @AfterEach
    void clearTenant() {
        RequestContext.clear();
    }

    private RoleEntity userRole(String id) {
        RoleEntity r = new RoleEntity();
        r.setId(id);
        r.setName("Some role");
        r.setIsBuiltIn(0);
        r.setMark(1);
        return r;
    }

    @Test
    void deleteWithoutForce_whenInUse_throwsInUseWithCountInDetail() {
        when(roleMapper.selectById("r1")).thenReturn(userRole("r1"));
        when(userRoleMapper.countActiveHoldersByRoleId(eq("r1"), eq("default"))).thenReturn(3L);

        assertThatThrownBy(() -> service.delete("r1", false))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.IN_USE);
                    assertThat(ex.getMessage()).contains("3 user");
                    assertThat(ex.detail()).isInstanceOf(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> d = (Map<String, Object>) ex.detail();
                    assertThat(d).containsEntry("users", 3L);
                });

        // Nothing soft-deleted on the role, link tables, or cache when we bail out early.
        verify(roleMapper, never()).update(any(), any());
        verify(cacheService, never()).evictRole(any());
    }

    @Test
    void deleteWithoutForce_whenNoUsers_softDeletesRoleViaUpdateWrapper() {
        when(roleMapper.selectById("r1")).thenReturn(userRole("r1"));
        when(userRoleMapper.countActiveHoldersByRoleId(eq("r1"), eq("default"))).thenReturn(0L);

        service.delete("r1", false);

        // The role itself: soft-delete must use UpdateWrapper.set, NOT setMark+updateById,
        // because @TableLogic strips the mark field from BaseMapper.updateById's SET clause.
        // MP wraps literal values into ew.paramNameValuePairs — assert the SET column and
        // that the param map carries the 0 value.
        ArgumentCaptor<UpdateWrapper<RoleEntity>> roleCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(roleMapper).update(eq(null), roleCap.capture());
        assertThat(roleCap.getValue().getSqlSet()).contains("mark=");
        assertThat(roleCap.getValue().getParamNameValuePairs().values()).contains(0);

        // Cascade: role_permission / role_menu / role_dept all soft-deleted.
        verify(rolePermissionMapper).update(eq(null), any(UpdateWrapper.class));
        verify(roleMenuMapper).update(eq(null), any(UpdateWrapper.class));
        verify(roleDeptMapper).update(eq(null), any(UpdateWrapper.class));

        // Cache evicted, user_role cleaned even when no users (idempotent, cheap).
        verify(cacheService).evictRole("r1");
        verify(userRoleMapper).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void deleteWithForce_clearsUserRoleAndEvictsCacheInRightOrder() {
        when(roleMapper.selectById("r1")).thenReturn(userRole("r1"));
        when(userRoleMapper.countActiveHoldersByRoleId(eq("r1"), eq("default"))).thenReturn(5L);

        service.delete("r1", true);

        // Cache evict must happen BEFORE user_role update — otherwise findUserIdsByRoleId
        // (which evictRole calls internally) returns empty after the cascade and we
        // never invalidate the affected users' caches.
        var inOrder = org.mockito.Mockito.inOrder(cacheService, userRoleMapper);
        inOrder.verify(cacheService).evictRole("r1");
        inOrder.verify(userRoleMapper).update(eq(null), any(UpdateWrapper.class));

        // user_role was indeed soft-deleted (zombie-link cleanup).
        verify(userRoleMapper, times(1)).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void deleteRefusesBuiltInRole() {
        RoleEntity superAdmin = new RoleEntity();
        superAdmin.setId("00000000000000000000ROLE01");
        superAdmin.setName("Super Administrator");
        superAdmin.setIsBuiltIn(1);
        superAdmin.setMark(1);
        when(roleMapper.selectById(superAdmin.getId())).thenReturn(superAdmin);

        assertThatThrownBy(() -> service.delete(superAdmin.getId(), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("read-only");

        verify(userRoleMapper, never()).countActiveHoldersByRoleId(any(), any());
    }
}
