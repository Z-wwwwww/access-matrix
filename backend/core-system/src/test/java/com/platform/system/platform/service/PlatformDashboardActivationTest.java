package com.platform.system.platform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every activation-funnel query must be restricted to the TENANT-ADMIN invite.
 *
 * <p>{@code core_user_invite} is shared: ordinary business users invited by a tenant's
 * own admin ({@code UserAdminService} INVITE mode) land in the same table under the
 * same {@code tenant_id}, and nothing in the row says which flow minted it. Counting
 * those as onboarding made the dashboard contradict itself — verified against the real
 * DB that one employee invite put {@code demo} (14 successful logins, therefore counted
 * as ACTIVATED) simultaneously into "pending activation", and the drill-down row was
 * labelled only with the tenant's code / display name / contact email, so it read
 * exactly like a customer who never activated. With the fix, the same data yields
 * pending=0, and pending=1 only once the admin's own invite is outstanding.
 *
 * <p>The service is hand-written SQL over {@link JdbcTemplate}, so this asserts on the
 * statements it issues; the semantics were checked directly against Postgres.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformDashboardActivationTest {

    @Mock JdbcTemplate jdbc;

    private PlatformDashboardService service;

    @BeforeEach
    void setUp() {
        service = new PlatformDashboardService(jdbc);
        when(jdbc.queryForObject(anyString(), any(Class.class))).thenReturn(null);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
    }

    /** All SQL the dashboard issued, in order. */
    private List<String> issuedSql() {
        service.load();
        ArgumentCaptor<String> counts = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForObject(counts.capture(), any(Class.class));
        ArgumentCaptor<String> lists = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).query(lists.capture(), any(RowMapper.class));
        return java.util.stream.Stream.concat(
                counts.getAllValues().stream(), lists.getAllValues().stream()).toList();
    }

    @Test
    void everyInviteQueryJoinsThroughTheBuiltInSuperAdminRole() {
        List<String> inviteSql = issuedSql().stream()
                .filter(s -> s.contains("core_user_invite"))
                .toList();

        // Two counts (pending / expired) + two lists (pending / expired).
        assertThat(inviteSql).hasSize(4);
        assertThat(inviteSql).allSatisfy(sql -> assertThat(sql)
                .as("invite query must be admin-scoped: %s", sql)
                .contains("core_rbac_user_role")
                .contains("core_rbac_role")
                .contains("is_built_in = 1"));
    }

    @Test
    void inviteQueriesStillExcludeTheSystemTenantAndUsedOrDeletedRows() {
        // The pre-existing filters must survive the rewrite.
        List<String> inviteSql = issuedSql().stream()
                .filter(s -> s.contains("core_user_invite"))
                .toList();

        assertThat(inviteSql).allSatisfy(sql -> assertThat(sql)
                .contains("i.used_at IS NULL")
                .contains("i.mark = 1")
                .contains("i.tenant_id NOT IN ('system')"));
    }

    @Test
    void theRoleJoinIsTenantScopedOnBothHops() {
        // core_rbac_user_role's FK references core_rbac_role(id) only — NOT the
        // tenant — so both joins must carry tenant_id or a cross-tenant link would
        // qualify an invite as "admin".
        List<String> inviteSql = issuedSql().stream()
                .filter(s -> s.contains("core_rbac_user_role"))
                .toList();

        assertThat(inviteSql).isNotEmpty().allSatisfy(sql -> assertThat(sql)
                .contains("ur.tenant_id = i.tenant_id")
                .contains("r.tenant_id = ur.tenant_id"));
    }

    @Test
    void nonInviteFunnelQueriesAreUntouched() {
        // The activated / median-time metrics key off login logs, not invites, and
        // must NOT have been narrowed by this change.
        List<String> sql = issuedSql();

        assertThat(sql).anySatisfy(s -> assertThat(s)
                .contains("core_tenant")
                .contains("core_auth_login_log")
                .doesNotContain("core_user_invite"));
    }
}
