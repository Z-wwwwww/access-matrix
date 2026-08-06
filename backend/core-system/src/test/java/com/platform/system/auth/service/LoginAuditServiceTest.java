package com.platform.system.auth.service;

import com.platform.system.auth.entity.LoginLogEntity;
import com.platform.system.auth.mapper.LoginLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * The failed-login audit row must survive attacker-controlled input lengths.
 *
 * <p>Every argument of {@code record} is reachable on an UNAUTHENTICATED
 * request: {@code identifier} is {@code LoginRequest.username} (annotated
 * {@code @NotBlank} only — no {@code @Size}), {@code userAgent} is the raw
 * User-Agent header, {@code tenantId} is the raw X-Tenant-Id header. Verified
 * against the real DB that {@code core_auth_login_log} rejects identifier/129,
 * user_agent/513 and tenant_id/65 outright, and {@code record} wraps its insert
 * in a catch-and-WARN — so the row silently never landed. That leaves the
 * Redis lockout counter incremented with nothing in the audit trail.
 */
@ExtendWith(MockitoExtension.class)
class LoginAuditServiceTest {

    @Mock LoginLogMapper mapper;
    @InjectMocks LoginAuditService service;

    private static String x(int n) {
        return "x".repeat(n);
    }

    private LoginLogEntity captured() {
        ArgumentCaptor<LoginLogEntity> cap = ArgumentCaptor.forClass(LoginLogEntity.class);
        verify(mapper).insert(cap.capture());
        return cap.getValue();
    }

    @Test
    void overlongAttackerInputStillProducesAnAuditRow() {
        service.record(x(200), "01J0000000000000000000000A", x(500), x(300),
                x(4000), false, x(400));

        LoginLogEntity e = captured();
        assertThat(e.getTenantId()).hasSize(64);
        assertThat(e.getIdentifier()).hasSize(128);
        assertThat(e.getClientIp()).hasSize(64);
        assertThat(e.getUserAgent()).hasSize(512);
        assertThat(e.getFailureReason()).hasSize(128);
    }

    @Test
    void normalValuesArePassedThroughUnchanged() {
        service.record("demo", "01J0000000000000000000000A", "alice@example.com",
                "127.0.0.1", "Mozilla/5.0", false, "bad-credentials");

        LoginLogEntity e = captured();
        assertThat(e.getTenantId()).isEqualTo("demo");
        assertThat(e.getIdentifier()).isEqualTo("alice@example.com");
        assertThat(e.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(e.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(e.getFailureReason()).isEqualTo("bad-credentials");
        assertThat(e.getSuccess()).isFalse();
    }

    @Test
    void exactWidthValuesAreNotTruncated() {
        service.record(x(64), "01J0000000000000000000000A", x(128), x(64),
                x(512), true, x(128));

        LoginLogEntity e = captured();
        assertThat(e.getTenantId()).hasSize(64);
        assertThat(e.getIdentifier()).hasSize(128);
        assertThat(e.getUserAgent()).hasSize(512);
        assertThat(e.getFailureReason()).hasSize(128);
    }

    @Test
    void nullsSurviveClamping_andBlankTenantStillFallsBackToDefault() {
        service.record("  ", null, null, null, null, true, null);

        LoginLogEntity e = captured();
        // The pre-existing blank-tenant fallback must not be broken by clamping.
        // The value tracks CoreRequestContextFilter's DEFAULT_TENANT: it used to be
        // "default", a tenant that stopped existing when V25 renamed it to "demo",
        // which would have filed the row where no tenant-scoped query can see it.
        assertThat(e.getTenantId()).isEqualTo("demo");
        assertThat(e.getIdentifier()).isNull();
        assertThat(e.getUserAgent()).isNull();
        assertThat(e.getFailureReason()).isNull();
    }
}
