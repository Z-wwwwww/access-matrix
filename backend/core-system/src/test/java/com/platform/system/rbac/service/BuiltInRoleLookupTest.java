package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link BuiltInRoleLookup}'s observable behavior:
 *
 *  - Resolution by name + is_built_in=1 + mark=1 + per-tenant scope.
 *  - Null on no match (cached, so the next call doesn't re-hit the DB).
 *  - Caching: repeated lookups on the same tenant fire RoleMapper once.
 *  - Invalidation drops the cached entry and forces a re-read.
 *  - Blank / null tenantId short-circuits with null (no DB call).
 */
@ExtendWith(MockitoExtension.class)
class BuiltInRoleLookupTest {

    @Mock RoleMapper roleMapper;

    private BuiltInRoleLookup lookup() {
        return new BuiltInRoleLookup(roleMapper);
    }

    private RoleEntity role(String id) {
        RoleEntity r = new RoleEntity();
        r.setId(id);
        return r;
    }

    @Test
    void resolvesByName_perTenant() {
        when(roleMapper.selectOne(any())).thenReturn(role("ACME-SUPER-ID"));

        BuiltInRoleLookup l = lookup();
        assertThat(l.superAdminRoleId("acme")).isEqualTo("ACME-SUPER-ID");
    }

    @Test
    void cachesPerTenant_doesNotHitDbAgainOnSecondCall() {
        when(roleMapper.selectOne(any())).thenReturn(role("DEMO-SUPER-ID"));

        BuiltInRoleLookup l = lookup();
        l.superAdminRoleId("demo");
        l.superAdminRoleId("demo");
        l.superAdminRoleId("demo");

        // Only one DB call despite three lookups — Caffeine LoadingCache behavior.
        verify(roleMapper, times(1)).selectOne(any(Wrapper.class));
    }

    @Test
    void cachesNullResult_doesNotKeepProbingOnMiss() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        BuiltInRoleLookup l = lookup();
        assertThat(l.superAdminRoleId("ghost")).isNull();
        assertThat(l.superAdminRoleId("ghost")).isNull();

        // Crucial: missing tenants don't hammer the DB on every check.
        // Optional.ofNullable wrapping in loadSuperAdminRoleId enables this —
        // Caffeine LoadingCache rejects raw null values.
        verify(roleMapper, times(1)).selectOne(any(Wrapper.class));
    }

    @Test
    void invalidate_dropsEntry_nextCallReReads() {
        when(roleMapper.selectOne(any())).thenReturn(role("V1")).thenReturn(role("V2"));

        BuiltInRoleLookup l = lookup();
        assertThat(l.superAdminRoleId("acme")).isEqualTo("V1");
        l.invalidate("acme");
        assertThat(l.superAdminRoleId("acme")).isEqualTo("V2");

        verify(roleMapper, times(2)).selectOne(any(Wrapper.class));
    }

    @Test
    void invalidateAll_dropsEverything() {
        when(roleMapper.selectOne(any())).thenReturn(role("A1")).thenReturn(role("A2"));

        BuiltInRoleLookup l = lookup();
        l.superAdminRoleId("acme");
        l.invalidateAll();
        l.superAdminRoleId("acme");

        verify(roleMapper, times(2)).selectOne(any(Wrapper.class));
    }

    @Test
    void blankTenantId_shortCircuitsToNull_noDbCall() {
        BuiltInRoleLookup l = lookup();
        assertThat(l.superAdminRoleId(null)).isNull();
        assertThat(l.superAdminRoleId("")).isNull();
        assertThat(l.superAdminRoleId("   ")).isNull();

        verify(roleMapper, org.mockito.Mockito.never()).selectOne(any());
    }
}
