package com.platform.core.infrastructure.security.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

/**
 * Translates a {@link DataScopeDecision} into SQL conditions on a MyBatis-Plus
 * {@link LambdaQueryWrapper}.
 *
 * <p>Usage in a service method:
 *
 * <pre>{@code
 * DataScopeDecision scope = dataScopeResolver.currentDecision();
 * LambdaQueryWrapper<Customer> w = new LambdaQueryWrapper<>();
 * w.eq(...business filters...);
 * DataScopeHelper.apply(w, scope, Customer::getDeptId, Customer::getCreateUser);
 * return mapper.selectPage(page, w);
 * }</pre>
 *
 * <p>The filter is wrapped in a single AND group so it cannot leak through
 * sibling OR clauses the caller may have already added.
 */
public final class DataScopeHelper {

    private DataScopeHelper() {}

    /**
     * Apply the decision to the wrapper. Both column references are required —
     * pass {@code null} to either one to disable that part of the filter
     * (rare; usually you have a {@code dept_id} and a {@code create_user}
     * on every business table).
     */
    public static <T> void apply(LambdaQueryWrapper<T> wrapper,
                                 DataScopeDecision decision,
                                 SFunction<T, ?> deptColumn,
                                 SFunction<T, ?> creatorColumn) {
        // Mark before the early returns: even an "unrestricted" decision means
        // the service consciously consulted scope, which is what the aspect
        // wants to confirm. Forgetting apply() entirely is the bug we catch.
        DataScopeContext.markApplied(wrapper);
        if (decision == null || decision.unrestricted()) return;
        if (decision.hasNoAccess()) {
            // No access at all → block every row. apply("1=0") is the standard "no-results" idiom
            // and the optimiser short-circuits before any table scan.
            wrapper.apply("1 = 0");
            return;
        }

        wrapper.and(group -> {
            boolean hasDept = !decision.visibleDeptIds().isEmpty() && deptColumn != null;
            boolean hasSelf = decision.selfOnly() && creatorColumn != null && decision.userId() != null;

            if (hasDept && hasSelf) {
                group.in(deptColumn, decision.visibleDeptIds())
                     .or().eq(creatorColumn, decision.userId());
            } else if (hasDept) {
                group.in(deptColumn, decision.visibleDeptIds());
            } else if (hasSelf) {
                group.eq(creatorColumn, decision.userId());
            } else {
                // Decision has flags but neither column is usable — fall back to no-access.
                group.apply("1 = 0");
            }
        });
    }

    /**
     * Row-level visibility check for <b>single-object</b> access (get / update /
     * delete by id). {@link #apply} scopes <em>list</em> queries at the SQL
     * layer; by-id endpoints fetch with {@code selectById} (tenant-scoped but
     * NOT data-scoped), so they must gate the fetched row through this method —
     * otherwise a DEPT/SELF-scoped caller can read or mutate any row in the
     * tenant by guessing its id (broken object-level authorization / IDOR).
     *
     * <p>Returns the same verdict {@link #apply}'s SQL would produce for that
     * row: {@code unrestricted} (or null decision) sees everything; no-access
     * sees nothing; otherwise visible iff the row's dept is in
     * {@code visibleDeptIds} OR ({@code selfOnly} and the caller created it).
     * A row with {@code deptId == null} is invisible to a DEPT-scoped caller
     * (fail-closed, matching {@code apply}'s {@code IN (...)} behavior).
     *
     * <p>On a negative verdict the caller should throw {@code NOT_FOUND} (not
     * {@code FORBIDDEN}) so the response does not reveal that the id exists.
     */
    public static boolean isVisible(DataScopeDecision decision, String deptId, String createUser) {
        if (decision == null || decision.unrestricted()) return true;
        if (decision.hasNoAccess()) return false;
        boolean deptVisible = deptId != null && decision.visibleDeptIds().contains(deptId);
        boolean selfVisible = decision.selfOnly()
                && decision.userId() != null
                && decision.userId().equals(createUser);
        return deptVisible || selfVisible;
    }
}
