package com.platform.system.rbac.service;

import com.platform.core.infrastructure.audit.OpLogRecord;
import com.platform.system.rbac.entity.OpLogEntity;
import com.platform.system.rbac.mapper.OpLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Every {@code core_oplog} varchar column must be clamped at the sink.
 *
 * <p>Why this matters: {@code OpLogService.record} wraps the insert in a
 * catch-and-WARN, so a Postgres {@code value too long for type character
 * varying(N)} does not fail the request — it silently drops the audit row.
 * Verified against the real DB that a 513-char {@code request_uri} is rejected
 * outright, and {@code OpLogAspect} passed {@code getRequestURI()} through
 * untruncated (it pre-truncated only user-agent and error message), as did the
 * pre-auth {@code recordAudit} helpers in InviteController /
 * PasswordResetController. Clamping at the single write point covers all
 * producers; these tests pin the widths against schema drift.
 */
@ExtendWith(MockitoExtension.class)
class OpLogServiceClampTest {

    @Mock OpLogMapper mapper;
    @InjectMocks OpLogService service;

    private static String x(int n) {
        return "x".repeat(n);
    }

    private OpLogEntity recordAndCapture(OpLogRecord r) {
        service.record(r);
        ArgumentCaptor<OpLogEntity> cap = ArgumentCaptor.forClass(OpLogEntity.class);
        verify(mapper).insert(cap.capture());
        return cap.getValue();
    }

    /** All-oversized record: nothing may exceed its column width. */
    @Test
    void everyBoundedColumnIsClampedToItsSchemaWidth() {
        OpLogEntity e = recordAndCapture(new OpLogRecord(
                x(200),                 // tenant_id      varchar(64)
                "01J0000000000000000000000A",
                x(200),                 // username       varchar(64)
                x(200),                 // module         varchar(32)
                x(200),                 // action         varchar(64)
                x(200),                 // target_type    varchar(32)
                x(200),                 // target_id      varchar(64)
                "/api/" + x(1000),      // request_uri    varchar(512)
                x(200),                 // method         varchar(8)
                x(200),                 // client_ip      varchar(64)
                x(2000),                // user_agent     varchar(512)
                x(100_000),             // request_body   text — unbounded, not clamped
                true,
                x(5000),                // error_msg      varchar(512)
                null,
                12));

        assertThat(e.getTenantId()).hasSize(64);
        assertThat(e.getUsername()).hasSize(64);
        assertThat(e.getModule()).hasSize(32);
        assertThat(e.getAction()).hasSize(64);
        assertThat(e.getTargetType()).hasSize(32);
        assertThat(e.getTargetId()).hasSize(64);
        assertThat(e.getRequestUri()).as("the regression: was passed through raw").hasSize(512);
        assertThat(e.getMethod()).hasSize(8);
        assertThat(e.getClientIp()).hasSize(64);
        assertThat(e.getUserAgent()).hasSize(512);
        assertThat(e.getErrorMsg()).hasSize(512);
        // request_body is TEXT — clamping it would corrupt the audit payload.
        assertThat(e.getRequestBody()).hasSize(100_000);
    }

    /** Ordinary values must pass through byte-identical — no off-by-one truncation. */
    @Test
    void normalValuesArePassedThroughUnchanged() {
        OpLogEntity e = recordAndCapture(new OpLogRecord(
                "demo", "01J0000000000000000000000A", "alice",
                "rbac", "user.create", "user", "01J0000000000000000000000B",
                "/api/system/users", "POST", "127.0.0.1", "Mozilla/5.0",
                "{\"username\":\"bob\"}", true, null, null, 42));

        assertThat(e.getTenantId()).isEqualTo("demo");
        assertThat(e.getUsername()).isEqualTo("alice");
        assertThat(e.getAction()).isEqualTo("user.create");
        assertThat(e.getRequestUri()).isEqualTo("/api/system/users");
        assertThat(e.getMethod()).isEqualTo("POST");
        assertThat(e.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(e.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(e.getErrorMsg()).isNull();
    }

    /** A value sitting exactly on the boundary must not lose its last character. */
    @Test
    void exactWidthValuesAreNotTruncated() {
        OpLogEntity e = recordAndCapture(new OpLogRecord(
                x(64), "01J0000000000000000000000A", x(64), x(32), x(64), x(32), x(64),
                x(512), x(8), x(64), x(512), null, true, x(512), null, 0));

        assertThat(e.getTenantId()).hasSize(64);
        assertThat(e.getRequestUri()).hasSize(512);
        assertThat(e.getMethod()).hasSize(8);
        assertThat(e.getUserAgent()).hasSize(512);
        assertThat(e.getErrorMsg()).hasSize(512);
    }

    /** Nulls must stay null, not become empty strings (the columns are nullable). */
    @Test
    void nullsSurviveClamping() {
        OpLogEntity e = recordAndCapture(new OpLogRecord(
                null, null, null, null, "x", null, null,
                null, null, null, null, null, false, null, 500, 1));

        assertThat(e.getTenantId()).isNull();
        assertThat(e.getUsername()).isNull();
        assertThat(e.getRequestUri()).isNull();
        assertThat(e.getUserAgent()).isNull();
        assertThat(e.getErrorMsg()).isNull();
    }
}
