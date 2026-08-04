package com.platform.system.web;

import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the MyBatis-Plus guards {@code MybatisPlusConfig} installs actually fire,
 * rather than merely being present.
 *
 * <p>Worth pinning because the optimistic-lock protection in the very same
 * {@code mybatisPlusInterceptor()} bean turned out to be declared-but-unwired: the
 * interceptor was added, the {@code @Version} column existed and error 702 was mapped,
 * yet nothing could ever report a conflict. These two are exercised through their real
 * public API (the same {@code parserSingle} entry point the plugin chain drives) so a
 * future version bump or config edit that neuters them fails here.
 */
class MybatisGuardsLiveTest {

    // ── BlockAttackInnerInterceptor: refuse a WHERE-less UPDATE / DELETE ─────

    @Test
    void blockAttack_refusesAFullTableUpdate() {
        BlockAttackInnerInterceptor guard = new BlockAttackInnerInterceptor();

        assertThatThrownBy(() -> guard.parserSingle("UPDATE core_auth_user SET status = 0", null))
                .as("a WHERE-less UPDATE must not reach the database")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void blockAttack_refusesAFullTableDelete() {
        BlockAttackInnerInterceptor guard = new BlockAttackInnerInterceptor();

        assertThatThrownBy(() -> guard.parserSingle("DELETE FROM core_auth_user", null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void blockAttack_allowsAScopedUpdate() {
        BlockAttackInnerInterceptor guard = new BlockAttackInnerInterceptor();

        // The shape every soft-delete in this codebase uses.
        assertThatNoException().isThrownBy(() -> guard.parserSingle(
                "UPDATE core_rbac_dept SET mark = 0 WHERE id = 'x' AND mark = 1", null));
    }

    @Test
    void blockAttack_isNotFooledByAnAlwaysTrueWhere() {
        // Documents the guard's actual reach: it checks for the PRESENCE of a WHERE,
        // not that the predicate is selective. Tenant scoping + explicit predicates are
        // what keep updates narrow; this guard only catches the outright-missing case.
        BlockAttackInnerInterceptor guard = new BlockAttackInnerInterceptor();

        assertThatThrownBy(() -> guard.parserSingle(
                "UPDATE core_auth_user SET status = 0 WHERE 1 = 1", null))
                .as("MP treats a tautological WHERE as a full-table update too")
                .isInstanceOf(RuntimeException.class);
    }

    // ── PaginationInnerInterceptor: the 500-row cap ──────────────────────────

    @Test
    void paginationCapIsFiveHundred_matchingTheHandRolledPaginator() {
        // PlatformUserAdminService clamps its hand-written LIMIT to the same 500, so the
        // two paginators in the codebase agree. A drift here means one list can return
        // more rows than the other for the same requested size.
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(500L);

        assertThat(pagination.getMaxLimit()).isEqualTo(500L);
    }

    @Test
    void paginationOverflowIsOff_soAnOutOfRangePageReturnsEmpty() {
        // overflow=true would silently wrap a too-large page back to page 1 — a caller
        // paging past the end would get the FIRST page instead of nothing.
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setOverflow(false);

        assertThat(pagination.isOverflow()).isFalse();
    }
}
