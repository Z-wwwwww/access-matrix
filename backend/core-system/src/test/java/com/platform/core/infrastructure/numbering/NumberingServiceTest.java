package com.platform.core.infrastructure.numbering;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins the tenantId fail-fast contract on every public allocation method.
 *
 * <p>Why this lives in a test: {@code core_numbering_key} is per-tenant
 * but is in {@link com.platform.core.infrastructure.config.MybatisPlusConfig#TENANT_EXCLUDED_TABLES}
 * because access goes through {@link JdbcTemplate} (the MP tenant interceptor
 * never sees these queries). That means correctness depends entirely on
 * {@link NumberingService} manually binding {@code tenant_id} on every SQL
 * — and on every <em>caller</em> passing a non-null tenantId.
 *
 * <p>The previous implementation had {@code tenantId == null ? "default" : tenantId}
 * which silently routed bad callers into a phantom {@code "default"} bucket
 * (the literal tenant name was renamed to {@code "demo"} by V25 anyway). This
 * test ensures any future regression that re-introduces silent fallback
 * fails here before it can leak counters across tenants.
 */
class NumberingServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NumberingService svc = new NumberingService(jdbc);

    @Test
    @DisplayName("next() rejects null tenantId — no DB access, BusinessException raised")
    void nextRejectsNull() {
        assertThatThrownBy(() -> svc.next("USER", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        verifyNoDbAccess();
    }

    @Test
    @DisplayName("next() rejects blank tenantId")
    void nextRejectsBlank() {
        assertThatThrownBy(() -> svc.next("USER", "   "))
                .isInstanceOf(BusinessException.class);
        verifyNoDbAccess();
    }

    @Test
    @DisplayName("collect() rejects null tenantId")
    void collectRejectsNull() {
        assertThatThrownBy(() -> svc.collect("USER", "BRANCH-1", null))
                .isInstanceOf(BusinessException.class);
        verifyNoDbAccess();
    }

    @Test
    @DisplayName("nextBatch() rejects null tenantId — even with valid count")
    void nextBatchRejectsNull() {
        assertThatThrownBy(() -> svc.nextBatch("USER", null, 10))
                .isInstanceOf(BusinessException.class);
        verifyNoDbAccess();
    }

    @Test
    @DisplayName("collectBatch() rejects null tenantId")
    void collectBatchRejectsNull() {
        assertThatThrownBy(() -> svc.collectBatch("USER", "BRANCH-1", null, 10))
                .isInstanceOf(BusinessException.class);
        verifyNoDbAccess();
    }

    @Test
    @DisplayName("error message tells the caller what to do (resolve RequestContext.tenantIdOrDefault)")
    void messageIsActionable() {
        assertThatThrownBy(() -> svc.next("USER", null))
                .hasMessageContaining("non-null tenantId")
                .hasMessageContaining("RequestContext");
    }

    private void verifyNoDbAccess() {
        // The point of fail-fast at the API boundary: the bad call must not
        // touch the DB at all. If a future refactor lets the call slip past
        // requireTenant() into doAllocate(), this assertion catches it.
        verify(jdbc, never()).query(anyString(), (org.springframework.jdbc.core.RowMapper<?>) any(), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
