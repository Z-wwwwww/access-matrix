package com.platform.core.infrastructure.security.rbac;

import java.util.Set;

/**
 * Result of resolving a user's data-scope at request time.
 *
 * <p>Semantics — the helper that consumes this decision must satisfy:
 * <ul>
 *   <li>{@code unrestricted == true}  → emit no filter (caller sees everything).</li>
 *   <li>{@code unrestricted == false} → emit
 *       {@code dept_id IN (visibleDeptIds)} OR (if {@code selfOnly})
 *       {@code create_user = userId}. The two clauses are OR'd so users with
 *       both DEPT and SELF roles see the union.</li>
 *   <li>{@code !unrestricted && visibleDeptIds.isEmpty() && !selfOnly} →
 *       caller has no data access at all; emit {@code 1=0} so the query
 *       returns an empty result instead of silently revealing rows.</li>
 * </ul>
 *
 * <p>Decision is computed once per request and may be Caffeine-cached
 * per (tenantId, userId) — see {@code DataScopeQueryService}.
 */
public record DataScopeDecision(
        boolean unrestricted,
        Set<String> visibleDeptIds,
        boolean selfOnly,
        String userId
) {

    public DataScopeDecision {
        if (visibleDeptIds == null) visibleDeptIds = Set.of();
    }

    public static DataScopeDecision unrestricted(String userId) {
        return new DataScopeDecision(true, Set.of(), false, userId);
    }

    public static DataScopeDecision empty(String userId) {
        return new DataScopeDecision(false, Set.of(), false, userId);
    }

    public boolean hasNoAccess() {
        return !unrestricted && visibleDeptIds.isEmpty() && !selfOnly;
    }
}
