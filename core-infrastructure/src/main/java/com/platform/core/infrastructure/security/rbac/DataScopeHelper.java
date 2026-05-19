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
}
