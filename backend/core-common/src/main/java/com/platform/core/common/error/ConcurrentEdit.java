package com.platform.core.common.error;

/**
 * Turns a lost optimistic-lock update into the {@link ErrorCode#OPTIMISTIC_LOCK_CONFLICT}
 * the API already declares.
 *
 * <h3>Why a helper is needed at all</h3>
 * {@code MybatisPlusConfig} installs {@code OptimisticLockerInnerInterceptor} and
 * {@code BaseEntity.updateTime} carries {@code @Version}, so every
 * {@code updateById(entity)} on a versioned entity is rewritten to
 * {@code ... SET update_time = <new> WHERE id = ? AND update_time = <the value that
 * was read>}. When a concurrent editor already advanced the version, that predicate
 * matches nothing.
 *
 * <p>Nothing reported it. The interceptor runs in {@code beforeUpdate} — structurally
 * before the statement executes — so it cannot know whether a row will match; its
 * opt-in {@code setException(...)} (unused here, and the project constructs the
 * no-arg form) covers a missing/unreadable version field, not a row-count miss. And
 * {@code GlobalExceptionHandler} maps Spring's {@code OptimisticLockingFailureException},
 * which MyBatis-Plus never throws. So the only place a lost update is observable is the
 * affected-row count returned by {@code updateById} — and no service inspected it.
 *
 * <p>Net effect before this: two admins editing the same role / menu / dict item / user
 * concurrently, and the second save silently did nothing while the UI reported success.
 * Verified against the real DB that the stale-version UPDATE reports {@code UPDATE 0}
 * and raises no error at all. That is the same "reports success, changed nothing" defect
 * the role-editor bind calls were fixed for.
 *
 * <h3>Why 0 rows is unambiguous here</h3>
 * Every caller loads the row first (its own {@code require(id)} / {@code loadVisibleOr404}),
 * so the row exists; and MyBatis-Plus always puts {@code update_time} in the SET clause,
 * so a matching row always reports 1. Therefore 0 means "the version moved", never
 * "nothing to change".
 */
public final class ConcurrentEdit {

    private ConcurrentEdit() {}

    /**
     * @param affectedRows what {@code updateById} returned
     * @throws BusinessException {@link ErrorCode#OPTIMISTIC_LOCK_CONFLICT} when the
     *                           update matched no row, i.e. someone else saved first
     */
    public static void requireApplied(int affectedRows) {
        if (affectedRows == 0) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "error.common.concurrentEdit");
        }
    }
}
