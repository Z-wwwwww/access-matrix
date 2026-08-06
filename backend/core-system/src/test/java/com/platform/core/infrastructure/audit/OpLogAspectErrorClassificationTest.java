package com.platform.core.infrastructure.audit;

import com.platform.core.common.audit.OpLog;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.infrastructure.security.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code core_oplog.error_code} is what the platform dashboard actually counts:
 * {@code PlatformDashboardService.reliability()} reads
 * {@code success = false AND error_code = 500} for its "API errors (24h)" KPI and
 * that KPI's drill-down. So the aspect's classification — not the HTTP status —
 * is what decides whether something pollutes the metric.
 *
 * <p>{@link DuplicateKeyException} is the case that has to be named explicitly.
 * The Spring MVC client errors (malformed JSON, bad param, wrong verb) never
 * reach the controller method, so this aspect never sees them. A duplicate key,
 * by contrast, is thrown from inside the service — inside this very advice — and
 * as a non-{@link BusinessException} it was recorded as 500. Mapping it to a
 * business error at the HTTP layer alone therefore left the KPI polluted by a
 * caller retyping a name that is already taken, which is precisely what that
 * mapping existed to prevent.
 */
class OpLogAspectErrorClassificationTest {

    @OpLog(module = "test", action = "thing.create", targetType = "thing")
    void audited() { /* annotation carrier only */ }

    private final List<OpLogRecord> captured = new ArrayList<>();

    private OpLogAspect aspect() {
        return new OpLogAspect(sinkProvider(captured::add), JsonMapper.builder().build(),
                new ClientIpResolver(false));
    }

    private OpLog annotation() throws Exception {
        return getClass().getDeclaredMethod("audited").getAnnotation(OpLog.class);
    }

    private ProceedingJoinPoint throwing(Throwable t) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{"arg"});
        when(pjp.proceed()).thenThrow(t);
        return pjp;
    }

    @Test
    void aDuplicateKeyIsRecordedAsABusinessError_soTheKpiStaysClean() throws Throwable {
        assertThatThrownBy(() -> aspect().around(
                throwing(new DuplicateKeyException("violates unique constraint \"uk_core_rbac_role_name\"")),
                annotation()))
                .isInstanceOf(DuplicateKeyException.class);   // still propagates

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).errorCode())
                .as("a taken name must not be counted as an unexpected server error")
                .isEqualTo(ErrorCode.BUSINESS_ERROR.code());
        assertThat(captured.get(0).success()).isFalse();
    }

    @Test
    void aBusinessExceptionKeepsItsOwnCode() throws Throwable {
        assertThatThrownBy(() -> aspect().around(
                throwing(new BusinessException(ErrorCode.IN_USE, "in use")), annotation()))
                .isInstanceOf(BusinessException.class);

        assertThat(captured.get(0).errorCode()).isEqualTo(ErrorCode.IN_USE.code());
    }

    @Test
    void arealFailureIsStillA500() throws Throwable {
        // The classification must stay narrow: an NPE is a server bug and has to
        // keep showing up in the KPI.
        assertThatThrownBy(() -> aspect().around(
                throwing(new NullPointerException("boom")), annotation()))
                .isInstanceOf(NullPointerException.class);

        assertThat(captured.get(0).errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.code());
    }

    private static ObjectProvider<OpLogSink> sinkProvider(OpLogSink sink) {
        return new ObjectProvider<>() {
            @Override public OpLogSink getObject(Object... args) { return sink; }
            @Override public OpLogSink getObject() { return sink; }
            @Override public OpLogSink getIfAvailable() { return sink; }
            @Override public OpLogSink getIfUnique() { return sink; }
            @Override public void ifAvailable(Consumer<OpLogSink> c) { c.accept(sink); }
            @Override public void ifUnique(Consumer<OpLogSink> c) { c.accept(sink); }
            @Override public OpLogSink getIfAvailable(Supplier<OpLogSink> s) { return sink; }
        };
    }
}
