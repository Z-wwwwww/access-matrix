package com.platform.system.platform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every KPI tile on the platform overview doubles as a drill-down button: clicking
 * it lists the rows behind the number. So a list query MUST carry the same time
 * window as the count it explains, or the console contradicts itself — the tile
 * says "0 in the last 24 hours" and the list it opens shows five rows.
 *
 * <p>This is the project's own stated rule: {@code recentOplogErrors} is bounded
 * with the comment "bounded to the same 24h window so the list and the count
 * agree". Three sibling lists were not.
 *
 * <p>Verified against the real dev database inside a rolled-back transaction: with
 * one {@code core_job_log} row at {@code now() - 3 days, status = 3}, the KPI query
 * returned 0 while the drill-down query returned 1 row.
 *
 * <p>The service is hand-written SQL over {@link JdbcTemplate}, so — following
 * {@link PlatformDashboardActivationTest} — this asserts on the statements issued.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformDashboardDrilldownWindowTest {

    @Mock JdbcTemplate jdbc;

    private PlatformDashboardService service;

    @BeforeEach
    void setUp() {
        service = new PlatformDashboardService(jdbc);
        when(jdbc.queryForObject(anyString(), any(Class.class))).thenReturn(null);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
    }

    /** Only the list (drill-down) statements — the ones behind a RowMapper. */
    private List<String> listSql() {
        service.load();
        ArgumentCaptor<String> lists = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).query(lists.capture(), any(RowMapper.class));
        return lists.getAllValues();
    }

    private String only(List<String> sql, String table, String... alsoContains) {
        List<String> hits = sql.stream()
                .filter(s -> s.contains(table))
                .filter(s -> {
                    for (String c : alsoContains) {
                        if (!s.contains(c)) return false;
                    }
                    return true;
                })
                .toList();
        assertThat(hits).as("expected exactly one list query for %s", table).hasSize(1);
        return hits.get(0);
    }

    @Test
    void recentJobFailuresIsBoundedToTheSame24hWindowAsItsKpi() {
        // Tile: "Job failures (24h)" (tooltip: "Scheduled-job runs that failed in
        // the last 24 hours"), KPI = COUNT(*) ... start_time >= now() - 24 hours.
        String sql = only(listSql(), "core_job_log");

        assertThat(sql)
                .as("drill-down for the 24h job-failure KPI must use the same window: %s", sql)
                .contains("start_time >= now() - INTERVAL '24 hours'");
    }

    @Test
    void recentBreakGlassIsBoundedToTheSame7dWindowAsItsKpi() {
        // Tile: "Break-glass (7d)", KPI = COUNT(*) ... create_time >= now() - 7 days.
        String sql = only(listSql(), "core_oplog", "auth.breakGlass", "client_ip");

        assertThat(sql)
                .as("drill-down for the 7d break-glass KPI must use the same window: %s", sql)
                .contains("create_time >= now() - INTERVAL '7 days'");
    }

    @Test
    void recentSupportSessionsIsBoundedToTheSame7dWindowAsItsKpi() {
        // The list backs two tiles: "Support live" (the frontend filters on the
        // per-row `active` flag) and "Support (7d)". A live session cannot fall
        // outside 7 days — the token TTL is 30 minutes — so bounding the query to
        // 7 days keeps both tiles honest without hiding an active session.
        String sql = only(listSql(), "core_support_session");

        assertThat(sql)
                .as("drill-down for the 7d support-session KPI must use the same window: %s", sql)
                .contains("started_at >= now() - INTERVAL '7 days'");
        assertThat(sql)
                .as("the per-row active flag must survive — it is what the 'Support live' tile filters on")
                .contains("ended_at IS NULL AND s.expires_at > now()");
    }

    @Test
    void recentOplogErrorsKeepsIts24hWindow() {
        // Regression guard: this one was already correct.
        String sql = only(listSql(), "core_oplog", "error_msg");

        assertThat(sql).contains("create_time >= now() - INTERVAL '24 hours'");
    }

    @Test
    void theEventBacklogListStaysUnbounded() {
        // Deliberately different: the backlog KPI itself has no time window
        // (every undispatched event counts, however old), so its list must not
        // acquire one either — an ancient stuck event is exactly what ops needs
        // to see.
        String sql = only(listSql(), "core_domain_event");

        assertThat(sql).doesNotContain("INTERVAL");
    }
}
