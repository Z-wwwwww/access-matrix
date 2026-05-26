package com.platform.system.rbac.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.security.PermissionMatcher;
import com.platform.core.infrastructure.security.rbac.DataScopeDecision;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.DeptEntity;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.mapper.DeptMapper;
import com.platform.system.rbac.mapper.RoleDeptMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins data-scope expansion across all 5 modes and the multi-role UNION rule.
 *
 * <p>Scope codes (must match seed migration V4):
 * <ul>
 *   <li>1 = ALL — short-circuits to unrestricted</li>
 *   <li>2 = DEPT_AND_SUB — current dept + descendants</li>
 *   <li>3 = DEPT — current dept only</li>
 *   <li>4 = SELF — only rows created by self ({@code selfOnly} flag)</li>
 *   <li>5 = CUSTOM — explicit dept set from {@code role_dept}, each expanded to its subtree</li>
 * </ul>
 *
 * <p>SUPER_ADMIN short-circuit: anyone holding {@code *:*} skips all role logic
 * and gets unrestricted scope.
 */
@ExtendWith(MockitoExtension.class)
class DataScopeQueryServiceTest {

    @Mock UserMapper userMapper;
    @Mock RoleMapper roleMapper;
    @Mock RoleDeptMapper roleDeptMapper;
    @Mock DeptMapper deptMapper;
    @Mock PermissionQueryService permissionQueryService;

    @InjectMocks DataScopeQueryService service;

    @BeforeEach
    void seedTenant() {
        RequestContext.set("acme", "tester", "tester", Locale.JAPAN, "trace-1");
    }

    @AfterEach
    void clearTenant() {
        RequestContext.clear();
    }

    private RoleEntity role(String id, int scope) {
        RoleEntity r = new RoleEntity();
        r.setId(id);
        r.setName("role-" + id);
        r.setDataScope(scope);
        r.setMark(1);
        return r;
    }

    private UserEntity userInDept(String id, String deptId) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setDeptId(deptId);
        u.setMark(1);
        return u;
    }

    private DeptEntity dept(String id, String path) {
        DeptEntity d = new DeptEntity();
        d.setId(id);
        d.setPath(path);
        d.setMark(1);
        return d;
    }

    @Test
    void nullOrBlankUserId_returnsEmptyDecision() {
        assertThat(service.loadDecision(null)).extracting(DataScopeDecision::userId).isNull();
        assertThat(service.loadDecision("")).extracting(DataScopeDecision::userId).isNull();
        assertThat(service.loadDecision("   ").hasNoAccess()).isTrue();
    }

    @Test
    void superAdminShortCircuitsToUnrestricted() {
        // The wildcard *:* check must happen BEFORE we even bother loading roles —
        // otherwise platform admins get filtered by their (possibly empty) role-bound depts.
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of(PermissionMatcher.SUPER));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.unrestricted()).isTrue();
        verify(roleMapper, never()).findRolesByUserId(any(), any());
    }

    @Test
    void noRoles_returnsEmptyDecision() {
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of());

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.unrestricted()).isFalse();
        assertThat(d.hasNoAccess()).isTrue();
    }

    @Test
    void scopeAll_shortCircuits_evenWithOtherRoles() {
        // ALL is the "most permissive" mode — once seen, we return unrestricted without
        // walking other roles. This is the multi-role UNION rule in action.
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of(
                role("r1", DataScopeQueryService.SCOPE_DEPT),
                role("r2", DataScopeQueryService.SCOPE_ALL)));
        lenient().when(userMapper.selectById("u1")).thenReturn(userInDept("u1", "d1"));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.unrestricted()).isTrue();
    }

    @Test
    void scopeDept_visibleSetIsJustOwnDept() {
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of(
                role("r1", DataScopeQueryService.SCOPE_DEPT)));
        when(userMapper.selectById("u1")).thenReturn(userInDept("u1", "d1"));
        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.unrestricted()).isFalse();
        assertThat(d.visibleDeptIds()).containsExactly("d1");
        assertThat(d.selfOnly()).isFalse();
        // Critical: DEPT mode must NOT call findSubtreeIds — it's the *bounded* scope.
        // (DEPT_AND_SUB is the one that walks the subtree.)
        verify(deptMapper, never()).findSubtreeIds(anyString(), anyString());
    }

    @Test
    void scopeDeptAndSub_expandsToSubtree() {
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of(
                role("r1", DataScopeQueryService.SCOPE_DEPT_AND_SUB)));
        when(userMapper.selectById("u1")).thenReturn(userInDept("u1", "d1"));
        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));
        when(deptMapper.findSubtreeIds("/d1", "acme")).thenReturn(List.of("d1", "d1-1", "d1-2"));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.visibleDeptIds()).containsExactlyInAnyOrder("d1", "d1-1", "d1-2");
        // Tenant must be threaded through — multi-tenant defense.
        verify(deptMapper).findSubtreeIds("/d1", "acme");
    }

    @Test
    void scopeSelf_setsFlagButNotDeptIds() {
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of(
                role("r1", DataScopeQueryService.SCOPE_SELF)));
        when(userMapper.selectById("u1")).thenReturn(userInDept("u1", "d1"));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.selfOnly()).isTrue();
        assertThat(d.visibleDeptIds()).isEmpty();
        // SELF must NOT touch role_dept or dept subtree lookups.
        verify(roleDeptMapper, never()).findDeptIdsByRoleId(any(), any());
        verify(deptMapper, never()).findSubtreeIds(any(), any());
    }

    @Test
    void scopeCustom_expandsEachAssignedDeptToItsSubtree() {
        // Repo convention: CUSTOM-mode role_dept rows define dept "heads"; each head
        // automatically includes its descendants. This makes CUSTOM intuitive for
        // setting "branch X + everyone under it" rather than enumerating every leaf.
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of(
                role("r1", DataScopeQueryService.SCOPE_CUSTOM)));
        when(userMapper.selectById("u1")).thenReturn(userInDept("u1", "d99"));
        when(deptMapper.selectById("d99")).thenReturn(dept("d99", "/d99"));
        when(roleDeptMapper.findDeptIdsByRoleId("r1", "acme")).thenReturn(List.of("dA", "dB"));
        when(deptMapper.selectById("dA")).thenReturn(dept("dA", "/dA"));
        when(deptMapper.selectById("dB")).thenReturn(dept("dB", "/d99/dB"));
        when(deptMapper.findSubtreeIds("/dA", "acme")).thenReturn(List.of("dA", "dA-1"));
        when(deptMapper.findSubtreeIds("/d99/dB", "acme")).thenReturn(List.of("dB", "dB-1", "dB-2"));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.visibleDeptIds()).containsExactlyInAnyOrder("dA", "dA-1", "dB", "dB-1", "dB-2");
    }

    @Test
    void multiRoleUnion_combinesDeptAndSelf() {
        // A user with both a DEPT role and a SELF role sees the UNION: their dept's rows
        // OR rows they created. The OR is intentional — UNION across modes, not intersection.
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of(
                role("r1", DataScopeQueryService.SCOPE_DEPT),
                role("r2", DataScopeQueryService.SCOPE_SELF)));
        when(userMapper.selectById("u1")).thenReturn(userInDept("u1", "d1"));
        when(deptMapper.selectById("d1")).thenReturn(dept("d1", "/d1"));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.visibleDeptIds()).containsExactly("d1");
        assertThat(d.selfOnly()).isTrue();
    }

    @Test
    void scopeDeptAndSub_userWithoutDept_returnsNoAccess() {
        // Defensive: a DEPT_AND_SUB user with no dept_id assigned shouldn't crash —
        // they just have no visibility (no dept means no subtree to walk).
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of(
                role("r1", DataScopeQueryService.SCOPE_DEPT_AND_SUB)));
        when(userMapper.selectById("u1")).thenReturn(userInDept("u1", null));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.hasNoAccess()).isTrue();
    }

    @Test
    void unknownScopeCode_doesNotCrash_andContributesNothing() {
        // Forward-compat: if a future migration adds a new scope code that runs against
        // old code, the unknown code should be silently ignored rather than killing
        // the request with NPE / unmatched switch.
        when(permissionQueryService.loadUserPermissions("u1")).thenReturn(Set.of("user:read"));
        when(roleMapper.findRolesByUserId("u1", "acme")).thenReturn(List.of(role("r1", 99)));
        lenient().when(userMapper.selectById("u1")).thenReturn(userInDept("u1", "d1"));

        DataScopeDecision d = service.loadDecision("u1");

        assertThat(d.hasNoAccess()).isTrue();
    }
}
