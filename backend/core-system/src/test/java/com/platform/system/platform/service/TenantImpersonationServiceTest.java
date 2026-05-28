package com.platform.system.platform.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.infrastructure.security.JwtIssuer;
import com.platform.system.platform.entity.TenantEntity;
import com.platform.system.platform.mapper.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pins TenantImpersonationService.startSession:
 *
 *   1. Built-in tenants (system, demo) refused without touching JdbcTemplate.
 *   2. Missing target SUPER_ADMIN role → NOT_FOUND with actionable message.
 *   3. Missing SUPER_ADMIN user holder → NOT_FOUND, distinct message.
 *   4. Happy path: act claim contains the original ops identity + reason
 *      + session_id; minted token uses the resolved user_id as sub and
 *      target tenant code as tid; username prefix is "[support] <ops>".
 *   5. Blank reason rejected (validation gate before any DB hit).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantImpersonationServiceTest {

    @Mock TenantMapper tenantMapper;
    @Mock JdbcTemplate jdbc;
    @Mock JwtIssuer jwtIssuer;

    private TenantImpersonationService service;

    @BeforeEach
    void setUp() {
        service = new TenantImpersonationService(tenantMapper, jdbc, jwtIssuer);
        // Simulate a logged-in platform ops user.
        RequestContext.set("system", "ULID-OPS-26-CHARS-XXXXXXXXX", "ops",
                Locale.JAPAN, "trace-id");
    }

    @AfterEach
    void clearCtx() {
        RequestContext.clear();
    }

    private TenantEntity row(String id, String code, String displayName) {
        TenantEntity e = new TenantEntity();
        e.setId(id);
        e.setTenantId("system");
        e.setTenantCode(code);
        e.setDisplayName(displayName);
        e.setStatus(1);
        e.setMark(1);
        return e;
    }

    @Test
    void blankReason_rejectedBeforeDb() {
        assertThatThrownBy(() -> service.startSession("id-acme", "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reason is required");
        // Validation gate fires before tenantMapper is even touched.
    }

    @Test
    void builtInSystem_refused() {
        when(tenantMapper.selectById("id-system")).thenReturn(row("id-system", "system", "System"));

        assertThatThrownBy(() -> service.startSession("id-system", "diag"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Built-in tenant");
    }

    @Test
    void builtInDemo_refused() {
        when(tenantMapper.selectById("id-demo")).thenReturn(row("id-demo", "demo", "Demo"));

        assertThatThrownBy(() -> service.startSession("id-demo", "diag"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Built-in tenant");
    }

    @Test
    void notFound_when_registry_missing() {
        when(tenantMapper.selectById("ghost")).thenReturn(null);
        assertThatThrownBy(() -> service.startSession("ghost", "diag"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void noSuperAdminRole_inTarget_failsWithActionableMessage() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme", "Acme"));
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("acme"), anyString()))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> service.startSession("id-acme", "OS-1234 reproduce"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no built-in SUPER_ADMIN role")
                .hasMessageContaining("RBAC seeder");
    }

    @Test
    void noSuperAdminUser_bound_failsWithActionableMessage() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme", "Acme"));
        // Role lookup succeeds, user lookup throws empty result.
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("acme"), anyString()))
                .thenReturn("ROLE-ID-ACME")  // role lookup
                .thenThrow(new EmptyResultDataAccessException(1));   // user lookup

        assertThatThrownBy(() -> service.startSession("id-acme", "OS-1234 reproduce"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no SUPER_ADMIN user to impersonate");
    }

    @Test
    void happyPath_mintsTokenWithActClaimAndPrefixedUsername() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme", "Acme Corp"));
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("acme"), anyString()))
                .thenReturn("ROLE-ID-ACME")
                .thenReturn("USER-ID-ACME-SUPER");

        Instant fakeExp = Instant.now().plus(Duration.ofMinutes(30));
        when(jwtIssuer.issueSupportSession(anyString(), anyString(), anyString(),
                anyString(), anyList(), any(), any()))
                .thenReturn(new JwtIssuer.TokenIssue("FAKE.JWT.TOKEN", "tok-id",
                        Instant.now(), fakeExp, 1800L));

        var resp = service.startSession("id-acme", "OS-1234 reproduce");

        assertThat(resp.token()).isEqualTo("FAKE.JWT.TOKEN");
        assertThat(resp.tenantCode()).isEqualTo("acme");
        assertThat(resp.displayName()).isEqualTo("Acme Corp");
        assertThat(resp.sessionId()).isNotBlank();
        assertThat(resp.expiresInSec()).isEqualTo(1800L);

        // Verify the call to JwtIssuer captured the right args.
        ArgumentCaptor<String> usernameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tidCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> actCap = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(jwtIssuer).issueSupportSession(
                subCap.capture(), tidCap.capture(), usernameCap.capture(),
                anyString(), anyList(), actCap.capture(), any());

        assertThat(subCap.getValue()).isEqualTo("USER-ID-ACME-SUPER");
        assertThat(tidCap.getValue()).isEqualTo("acme");
        // Audit prefix: every downstream oplog row will read as a support action.
        assertThat(usernameCap.getValue()).isEqualTo("[support] ops");

        // act claim captures the original ops identity + the reason supplied.
        Map<String, Object> act = actCap.getValue();
        assertThat(act).containsEntry("sub", "ULID-OPS-26-CHARS-XXXXXXXXX");
        assertThat(act).containsEntry("tid", "system");
        assertThat(act).containsEntry("username", "ops");
        assertThat(act).containsEntry("reason", "OS-1234 reproduce");
        assertThat(act).containsEntry("mode", "FULL");
        assertThat(act).containsKey("session_id");
    }
}
