package com.platform.core.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A lost optimistic-lock update must surface as {@link ErrorCode#OPTIMISTIC_LOCK_CONFLICT}.
 *
 * <p>The protection was declared in three places and wired in none: the interceptor is
 * installed, {@code BaseEntity.updateTime} is {@code @Version}, and
 * {@code GlobalExceptionHandler} maps error 702 — but MyBatis-Plus never throws
 * Spring's {@code OptimisticLockingFailureException}, and its interceptor runs in
 * {@code beforeUpdate}, i.e. structurally before the statement can be known to match a
 * row. The only observable signal is the affected-row count, and no service read it.
 * Verified against the real DB that a stale-version UPDATE reports {@code UPDATE 0}
 * with no error, so the second of two concurrent editors silently lost their change
 * while the UI reported success.
 */
class ConcurrentEditTest {

    @Test
    void zeroAffectedRowsIsAConflict() {
        assertThatThrownBy(() -> ConcurrentEdit.requireApplied(0))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
                    // i18n KEY, not prose — the SPA's localizeError resolves it.
                    assertThat(ex.getMessage()).isEqualTo("error.common.concurrentEdit");
                });
    }

    @Test
    void oneAffectedRowIsTheNormalCase() {
        assertThatNoException().isThrownBy(() -> ConcurrentEdit.requireApplied(1));
    }

    @Test
    void moreThanOneRowIsNotTreatedAsAConflict() {
        // Defensive: these call sites update by primary key so >1 cannot happen, but the
        // helper must never turn an over-broad update into a "someone else saved" error.
        assertThatNoException().isThrownBy(() -> ConcurrentEdit.requireApplied(2));
    }

    @Test
    void theDeclaredErrorCodeIsThe702TheApiAlreadyPublishes() {
        // Pins the contract the frontend and GlobalExceptionHandler both key on.
        assertThat(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.code()).isEqualTo(702);
    }
}
