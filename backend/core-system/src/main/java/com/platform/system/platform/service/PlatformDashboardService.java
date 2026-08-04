package com.platform.system.platform.service;

import com.platform.core.common.time.AppTime;
import com.platform.system.platform.dto.PlatformDashboardDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregations for the platform-ops monitoring dashboard (activation
 * funnel / engagement / reliability / security). All queries go through raw
 * {@link JdbcTemplate} so the MyBatis tenant interceptor stays out of the way —
 * these are deliberately cross-tenant, fleet-wide reads run from the 'system'
 * platform-ops context. No state change → no domain event.
 *
 * <p>Conventions used throughout:
 * <ul>
 *   <li>Only the {@code system} tenant is excluded from customer-facing metrics
 *       (activation, engagement) — it's a platform internal, not a customer.
 *       {@code demo} is an ordinary (seeded) customer tenant and IS counted.</li>
 *   <li>Business rows carry {@code tenant_id = tenant_code}; the registry's own
 *       {@code core_tenant.tenant_id} is always {@code 'system'}, so joins to
 *       login/invite data are on {@code core_tenant.tenant_code}.</li>
 *   <li>{@code core_job_log.status}: 1=running, 2=success, 3=fail.
 *       {@code core_domain_event.dispatch_state}: 0=pending, 1=dispatched, 2=failed.</li>
 * </ul>
 */
@Service
public class PlatformDashboardService {

    /** Cap for every "needs attention" list — these are actionable, not feeds. */
    private static final int LIST_CAP = 8;

    private final JdbcTemplate jdbc;

    public PlatformDashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PlatformDashboardDto.View load() {
        return new PlatformDashboardDto.View(activation(), engagement(), reliability(), security());
    }

    /**
     * Restricts an invite query to the TENANT-ADMIN invite — the one minted at tenant
     * creation for the holder of that tenant's built-in SUPER_ADMIN role.
     *
     * <p>Mandatory for every activation-funnel query. {@code core_user_invite} is
     * shared: ordinary business users invited by a tenant's own admin
     * ({@code UserAdminService} INVITE mode) land in the same table under the same
     * {@code tenant_id}. Counting those as onboarding makes the funnel contradict
     * itself — verified against the real DB that a single employee invite put
     * {@code demo} (14 successful logins, i.e. counted as ACTIVATED) simultaneously
     * into "pending activation", with a drill-down row labelled only with the
     * tenant's code / display name / contact email, hence indistinguishable from a
     * tenant that never onboarded. Ops then chases a customer who is already live.
     *
     * <p>{@code assignRoles} refuses to grant SUPER_ADMIN to a second user, so the
     * holder is unique; {@code is_built_in = 1} identifies the role without depending
     * on its name.
     */
    private static final String ADMIN_INVITE_ONLY =
            "  JOIN core_rbac_user_role ur "
                    + "    ON ur.user_id = i.user_id AND ur.tenant_id = i.tenant_id AND ur.mark = 1 "
                    + "  JOIN core_rbac_role r "
                    + "    ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id "
                    + "   AND r.mark = 1 AND r.is_built_in = 1 ";

    // ── 2. Activation funnel ───────────────────────────────────────────────
    private PlatformDashboardDto.Activation activation() {
        long pending = q1Long(
                "SELECT COUNT(DISTINCT i.tenant_id) FROM core_user_invite i "
                        + ADMIN_INVITE_ONLY
                        + "WHERE i.used_at IS NULL AND i.mark = 1 AND i.expires_at > now() "
                        + "AND i.tenant_id NOT IN ('system')");
        long expired = q1Long(
                "SELECT COUNT(DISTINCT i.tenant_id) FROM core_user_invite i "
                        + ADMIN_INVITE_ONLY
                        + "WHERE i.used_at IS NULL AND i.mark = 1 AND i.expires_at <= now() "
                        + "AND i.tenant_id NOT IN ('system')");

        long nonBuiltin = q1Long(
                "SELECT COUNT(*) FROM core_tenant WHERE mark = 1 "
                        + "AND tenant_code NOT IN ('system')");
        long activated = q1Long(
                "SELECT COUNT(*) FROM core_tenant t WHERE t.mark = 1 "
                        + "AND t.tenant_code NOT IN ('system') "
                        + "AND EXISTS (SELECT 1 FROM core_auth_login_log l "
                        + "            WHERE l.tenant_id = t.tenant_code AND l.success = true)");
        double rate = nonBuiltin == 0 ? 0.0 : (double) activated / nonBuiltin;

        Double medianHours = q1Double(
                "SELECT percentile_cont(0.5) WITHIN GROUP "
                        + "(ORDER BY EXTRACT(EPOCH FROM (first_login - create_time)) / 3600.0) "
                        + "FROM (SELECT t.create_time, MIN(l.login_time) AS first_login "
                        + "      FROM core_tenant t "
                        + "      JOIN core_auth_login_log l "
                        + "        ON l.tenant_id = t.tenant_code AND l.success = true "
                        + "      WHERE t.mark = 1 AND t.tenant_code NOT IN ('system') "
                        + "      GROUP BY t.id, t.create_time) x");

        // Still-valid pending invites (soonest-expiring first).
        List<PlatformDashboardDto.PendingInvite> pendingList = invites(
                "AND i.expires_at > now() ORDER BY i.expires_at ASC", false);
        // Expired-unused invites (most-recently-expired first).
        List<PlatformDashboardDto.PendingInvite> expiredList = invites(
                "AND i.expires_at <= now() ORDER BY i.expires_at DESC", true);

        return new PlatformDashboardDto.Activation(pending, expired, rate, medianHours, pendingList, expiredList);
    }

    /**
     * Shared invite-list query for the pending / expired activation cards.
     * Admin-invite-only, for the reason spelled out on {@link #ADMIN_INVITE_ONLY} —
     * these rows are labelled with the TENANT's identity, so an employee's invite
     * appearing here reads as "this customer never activated".
     */
    private List<PlatformDashboardDto.PendingInvite> invites(String tail, boolean expired) {
        return jdbc.query(
                "SELECT t.id, t.tenant_code, t.display_name, t.contact_email, "
                        + "       i.create_time AS invited_at, i.expires_at "
                        + "FROM core_user_invite i "
                        + "JOIN core_tenant t ON t.tenant_code = i.tenant_id AND t.mark = 1 "
                        + ADMIN_INVITE_ONLY
                        + "WHERE i.used_at IS NULL AND i.mark = 1 "
                        + "AND i.tenant_id NOT IN ('system') " + tail + " LIMIT " + LIST_CAP,
                (rs, n) -> new PlatformDashboardDto.PendingInvite(
                        rs.getString("id"), rs.getString("tenant_code"),
                        rs.getString("display_name"), rs.getString("contact_email"),
                        ts(rs.getObject("invited_at")), ts(rs.getObject("expires_at")),
                        expired));
    }

    // ── 3. Engagement ──────────────────────────────────────────────────────
    private PlatformDashboardDto.Engagement engagement() {
        long active7d = q1Long(
                "SELECT COUNT(DISTINCT tenant_id) FROM core_auth_login_log "
                        + "WHERE success = true AND tenant_id NOT IN ('system') "
                        + "AND login_time >= now() - INTERVAL '7 days'");
        long active30d = q1Long(
                "SELECT COUNT(DISTINCT tenant_id) FROM core_auth_login_log "
                        + "WHERE success = true AND tenant_id NOT IN ('system') "
                        + "AND login_time >= now() - INTERVAL '30 days'");
        long dau = q1Long(
                "SELECT COUNT(DISTINCT user_id) FROM core_auth_login_log "
                        + "WHERE success = true AND tenant_id NOT IN ('system') "
                        + "AND login_time >= now() - INTERVAL '1 day'");
        long mau = q1Long(
                "SELECT COUNT(DISTINCT user_id) FROM core_auth_login_log "
                        + "WHERE success = true AND tenant_id NOT IN ('system') "
                        + "AND login_time >= now() - INTERVAL '30 days'");

        String silentBase =
                "FROM core_tenant t WHERE t.mark = 1 AND t.status = 1 "
                        + "AND t.tenant_code NOT IN ('system') "
                        + "AND NOT EXISTS (SELECT 1 FROM core_auth_login_log l "
                        + "                WHERE l.tenant_id = t.tenant_code AND l.success = true "
                        + "                  AND l.login_time >= now() - INTERVAL '30 days')";
        long silentCount = q1Long("SELECT COUNT(*) " + silentBase);
        List<PlatformDashboardDto.SilentTenant> silent = jdbc.query(
                "SELECT t.id, t.tenant_code, t.display_name, "
                        + "(SELECT MAX(l.login_time) FROM core_auth_login_log l "
                        + " WHERE l.tenant_id = t.tenant_code AND l.success = true) AS last_login "
                        + silentBase
                        + " ORDER BY last_login ASC NULLS FIRST LIMIT " + LIST_CAP,
                (rs, n) -> new PlatformDashboardDto.SilentTenant(
                        rs.getString("id"), rs.getString("tenant_code"),
                        rs.getString("display_name"), ts(rs.getObject("last_login"))));

        // Dense 14-day login-success trend; gaps filled with 0 in Java.
        // Day buckets are business-time (JST) calendar decisions: bucket the
        // timestamptz instants in AppTime.zone() and convert the window edge back
        // to an instant, so the result is independent of the DB session TimeZone.
        String tz = AppTime.zone().getId();
        Map<String, Long> byDay = new HashMap<>();
        jdbc.queryForList(
                "SELECT to_char(login_time AT TIME ZONE '" + tz + "', 'YYYY-MM-DD') AS d, COUNT(*) AS c "
                        + "FROM core_auth_login_log "
                        + "WHERE success = true AND tenant_id NOT IN ('system') "
                        + "AND login_time >= (date_trunc('day', now() AT TIME ZONE '" + tz + "') "
                        + "                   - INTERVAL '13 days') AT TIME ZONE '" + tz + "' "
                        + "GROUP BY 1")
                .forEach(r -> byDay.put((String) r.get("d"), ((Number) r.get("c")).longValue()));
        List<PlatformDashboardDto.DailyCount> trend = new ArrayList<>(14);
        LocalDate cursor = LocalDate.now(AppTime.zone()).minusDays(13);
        for (int i = 0; i < 14; i++) {
            String label = cursor.toString();   // 'YYYY-MM-DD'
            trend.add(new PlatformDashboardDto.DailyCount(label, byDay.getOrDefault(label, 0L)));
            cursor = cursor.plusDays(1);
        }

        return new PlatformDashboardDto.Engagement(active7d, active30d, dau, mau, silentCount, silent, trend);
    }

    // ── 4. Reliability ─────────────────────────────────────────────────────
    private PlatformDashboardDto.Reliability reliability() {
        long jobFail = q1Long(
                "SELECT COUNT(*) FROM core_job_log WHERE status = 3 "
                        + "AND start_time >= now() - INTERVAL '24 hours'");
        long jobRuns = q1Long(
                "SELECT COUNT(*) FROM core_job_log WHERE status IN (2,3) "
                        + "AND start_time >= now() - INTERVAL '24 hours'");
        double failRate = jobRuns == 0 ? 0.0 : (double) jobFail / jobRuns;

        long eventPending = q1Long("SELECT COUNT(*) FROM core_domain_event WHERE dispatch_state = 0");
        long eventFailed  = q1Long("SELECT COUNT(*) FROM core_domain_event WHERE dispatch_state = 2");
        Long oldestMin = q1LongOrNull(
                "SELECT CAST(EXTRACT(EPOCH FROM (now() - MIN(occurred_at))) / 60 AS BIGINT) "
                        + "FROM core_domain_event WHERE dispatch_state <> 1");

        // Only unexpected server errors (error_code = 500) count — deliberate
        // BusinessException rejections (4xx/7xx) are normal outcomes, not errors.
        long oplogErr = q1Long(
                "SELECT COUNT(*) FROM core_oplog WHERE success = false AND error_code = 500 "
                        + "AND create_time >= now() - INTERVAL '24 hours'");

        List<PlatformDashboardDto.JobFailure> recent = jdbc.query(
                "SELECT job_code, start_time, duration_ms, error FROM core_job_log "
                        + "WHERE status = 3 ORDER BY start_time DESC LIMIT " + LIST_CAP,
                (rs, n) -> new PlatformDashboardDto.JobFailure(
                        rs.getString("job_code"), ts(rs.getObject("start_time")),
                        (Long) rs.getObject("duration_ms"), rs.getString("error")));

        // Drill-down for the "API errors (24h)" KPI: the actual failed requests,
        // bounded to the same 24h window so the list and the count agree.
        List<PlatformDashboardDto.OplogError> recentErrors = jdbc.query(
                "SELECT module, action, username, error_msg, create_time FROM core_oplog "
                        + "WHERE success = false AND error_code = 500 "
                        + "AND create_time >= now() - INTERVAL '24 hours' "
                        + "ORDER BY create_time DESC LIMIT " + LIST_CAP,
                (rs, n) -> new PlatformDashboardDto.OplogError(
                        rs.getString("module"), rs.getString("action"),
                        rs.getString("username"), rs.getString("error_msg"),
                        ts(rs.getObject("create_time"))));

        // Undispatched outbox events, oldest first (so the "oldest backlog" card's
        // detail leads with the worst offender).
        List<PlatformDashboardDto.BacklogEvent> backlog = jdbc.query(
                "SELECT aggregate_type, event_type, occurred_at, dispatch_state, dispatch_attempts "
                        + "FROM core_domain_event WHERE dispatch_state <> 1 "
                        + "ORDER BY occurred_at ASC LIMIT " + LIST_CAP,
                (rs, n) -> new PlatformDashboardDto.BacklogEvent(
                        rs.getString("aggregate_type"), rs.getString("event_type"),
                        ts(rs.getObject("occurred_at")), rs.getInt("dispatch_state"),
                        rs.getInt("dispatch_attempts")));

        return new PlatformDashboardDto.Reliability(
                jobFail, jobRuns, failRate, eventPending, eventFailed, oldestMin, oplogErr,
                recent, recentErrors, backlog);
    }

    // ── 5. Security ────────────────────────────────────────────────────────
    private PlatformDashboardDto.Security security() {
        // Truly live sessions (started, not ended, not expired) — from the
        // server-side session record (V52), not a 30-min proxy over start events.
        long activeSupport = q1Long(
                "SELECT COUNT(*) FROM core_support_session WHERE ended_at IS NULL AND expires_at > now()");
        long support7d = q1Long(
                "SELECT COUNT(*) FROM core_support_session WHERE started_at >= now() - INTERVAL '7 days'");
        long breakGlass7d = q1Long(
                "SELECT COUNT(*) FROM core_oplog "
                        + "WHERE module = 'system' AND action = 'auth.breakGlass' "
                        + "AND create_time >= now() - INTERVAL '7 days'");
        long loginFail = q1Long(
                "SELECT COUNT(*) FROM core_auth_login_log WHERE success = false "
                        + "AND login_time >= now() - INTERVAL '24 hours'");
        long pwdReset = q1Long(
                "SELECT COUNT(*) FROM core_password_reset_token "
                        + "WHERE create_time >= now() - INTERVAL '7 days'");

        // Recent sessions (newest first), incl. ended/expired — the "active" flag
        // marks the ones still live so the list and the KPI agree.
        List<PlatformDashboardDto.SupportSession> recent = jdbc.query(
                "SELECT s.operator, s.tenant_code, t.display_name, s.started_at, s.reason, "
                        + "       (s.ended_at IS NULL AND s.expires_at > now()) AS active "
                        + "FROM core_support_session s "
                        + "LEFT JOIN core_tenant t ON t.tenant_code = s.tenant_code "
                        + "ORDER BY s.started_at DESC LIMIT " + LIST_CAP,
                (rs, n) -> new PlatformDashboardDto.SupportSession(
                        rs.getString("operator"), rs.getString("tenant_code"),
                        rs.getString("display_name"), ts(rs.getObject("started_at")),
                        rs.getString("reason"), rs.getBoolean("active")));

        // Break-glass logins (oplog.tenant_id IS the tenant code for these rows).
        List<PlatformDashboardDto.BreakGlassUse> breakGlass = jdbc.query(
                "SELECT username, tenant_id, create_time, client_ip FROM core_oplog "
                        + "WHERE module = 'system' AND action = 'auth.breakGlass' "
                        + "ORDER BY create_time DESC LIMIT " + LIST_CAP,
                (rs, n) -> new PlatformDashboardDto.BreakGlassUse(
                        rs.getString("username"), rs.getString("tenant_id"),
                        ts(rs.getObject("create_time")), rs.getString("client_ip")));

        return new PlatformDashboardDto.Security(
                activeSupport, support7d, breakGlass7d, loginFail, pwdReset, recent, breakGlass);
    }

    // ── helpers ────────────────────────────────────────────────────────────
    private long q1Long(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }

    private Long q1LongOrNull(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private Double q1Double(String sql) {
        return jdbc.queryForObject(sql, Double.class);
    }

    /** PG timestamptz comes back as java.sql.Timestamp (a true instant); normalise to OffsetDateTime (null-safe). */
    private static java.time.OffsetDateTime ts(Object o) {
        return o == null ? null : ((java.sql.Timestamp) o).toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
}
