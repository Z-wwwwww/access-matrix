package com.platform.core.infrastructure.security.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards {@link DataScopeHelper#isVisible} — the row-level gate that stops
 * single-object (get/update/delete-by-id) endpoints from leaking or mutating
 * rows outside the caller's data scope (the IDOR fixed in business modules).
 * The verdicts must match what {@link DataScopeHelper#apply}'s SQL would
 * return for the same row.
 */
class DataScopeHelperTest {

    private static final String ME = "user-self";
    private static final String DEPT_OK = "DEPT-OSAKA";
    private static final String DEPT_OTHER = "DEPT-HQ";

    @Test
    @DisplayName("null decision and unrestricted see every row")
    void unrestrictedSeesAll() {
        assertThat(DataScopeHelper.isVisible(null, DEPT_OTHER, "someone")).isTrue();
        assertThat(DataScopeHelper.isVisible(DataScopeDecision.unrestricted(ME), DEPT_OTHER, "someone")).isTrue();
    }

    @Test
    @DisplayName("no-access decision sees nothing")
    void noAccessSeesNothing() {
        assertThat(DataScopeHelper.isVisible(DataScopeDecision.empty(ME), DEPT_OK, ME)).isFalse();
    }

    @Test
    @DisplayName("DEPT scope: visible only for rows whose dept is in the visible set")
    void deptScope() {
        DataScopeDecision dept = new DataScopeDecision(false, Set.of(DEPT_OK), false, ME);
        assertThat(DataScopeHelper.isVisible(dept, DEPT_OK, "anyone")).isTrue();
        assertThat(DataScopeHelper.isVisible(dept, DEPT_OTHER, "anyone")).isFalse();
        // null dept row is fail-closed for a DEPT-scoped caller (matches apply's IN(...))
        assertThat(DataScopeHelper.isVisible(dept, null, "anyone")).isFalse();
    }

    @Test
    @DisplayName("SELF scope: visible only for rows the caller created")
    void selfScope() {
        DataScopeDecision self = new DataScopeDecision(false, Set.of(), true, ME);
        assertThat(DataScopeHelper.isVisible(self, DEPT_OTHER, ME)).isTrue();
        assertThat(DataScopeHelper.isVisible(self, DEPT_OTHER, "other-creator")).isFalse();
    }

    @Test
    @DisplayName("DEPT + SELF union: visible if either dept matches or caller created it")
    void deptAndSelfUnion() {
        DataScopeDecision both = new DataScopeDecision(false, Set.of(DEPT_OK), true, ME);
        assertThat(DataScopeHelper.isVisible(both, DEPT_OK, "other")).isTrue();   // dept match
        assertThat(DataScopeHelper.isVisible(both, DEPT_OTHER, ME)).isTrue();     // self match
        assertThat(DataScopeHelper.isVisible(both, DEPT_OTHER, "other")).isFalse(); // neither
    }
}
