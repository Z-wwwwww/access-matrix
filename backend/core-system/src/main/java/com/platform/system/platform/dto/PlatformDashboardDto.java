package com.platform.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Platform-ops monitoring dashboard payload — the cross-cutting fleet-health
 * metrics shown on the platform console alongside the tenant KPIs. Distinct from
 * {@link TenantDto.Stats} (tenant counts / signup trend): this aggregates four
 * operational lenses that span auth / jobs / outbox / audit, not just the tenant
 * registry.
 *
 * <p>All counts are live aggregates over existing tables (no mock data); the
 * "list" fields are short, actionable "needs attention" lists (capped) meant to
 * be clicked through, not exhaustive feeds.
 */
public final class PlatformDashboardDto {

    private PlatformDashboardDto() {}

    public record View(
            Activation activation,
            Engagement engagement,
            Reliability reliability,
            Security security
    ) {}

    // ── 2. Onboarding / activation funnel ──────────────────────────────────
    /**
     * @param pendingTenants     tenants with an unused, unexpired admin invite
     * @param expiredUnactivated tenants whose admin invite expired never used
     * @param activationRate     fraction of non-built-in tenants with ≥1 successful login (0..1)
     * @param medianOnboardingHours median(first successful login − tenant creation), null if none
     * @param pending            actionable list of pending invites (capped)
     */
    public record Activation(
            long pendingTenants,
            long expiredUnactivated,
            double activationRate,
            Double medianOnboardingHours,
            List<PendingInvite> pending,
            List<PendingInvite> expired
    ) {}

    public record PendingInvite(
            String tenantId,
            String tenantCode,
            String displayName,
            String contactEmail,
            OffsetDateTime invitedAt,
            OffsetDateTime expiresAt,
            boolean expired
    ) {}

    // ── 3. Engagement ──────────────────────────────────────────────────────
    public record Engagement(
            long activeTenants7d,
            long activeTenants30d,
            long dau,
            long mau,
            long silentTenantsCount,
            List<SilentTenant> silentTenants,
            List<DailyCount> loginTrend
    ) {}

    /** Active, non-built-in tenant with no successful login in the last 30 days. */
    public record SilentTenant(
            String tenantId,
            String tenantCode,
            String displayName,
            OffsetDateTime lastLoginAt   // nullable: never logged in
    ) {}

    public record DailyCount(
            String day,                 // 'YYYY-MM-DD'
            long count
    ) {}

    // ── 4. Reliability ─────────────────────────────────────────────────────
    /**
     * @param eventBacklogOldestMin age (minutes) of the oldest undispatched outbox
     *                              event, null when the outbox is empty
     */
    public record Reliability(
            long jobFailures24h,
            long jobRuns24h,
            double jobFailureRate,
            long eventPending,
            long eventFailed,
            Long eventBacklogOldestMin,
            long oplogErrors24h,
            List<JobFailure> recentJobFailures,
            List<OplogError> recentOplogErrors,
            List<BacklogEvent> backlogEvents
    ) {}

    /** An undispatched outbox event (core_domain_event.dispatch_state != 1). */
    public record BacklogEvent(
            String aggregateType,
            String eventType,
            OffsetDateTime occurredAt,
            int dispatchState,   // 0=pending, 2=failed
            int attempts
    ) {}

    public record JobFailure(
            String jobCode,
            OffsetDateTime startTime,
            Long durationMs,
            String error
    ) {}

    /** A failed request (core_oplog.success = false) in the last 24h. */
    public record OplogError(
            String module,
            String action,
            String username,
            String errorMsg,
            OffsetDateTime time
    ) {}

    // ── 5. Security & privileged-access monitoring ─────────────────────────
    /**
     * @param activeSupportSessions impersonation sessions started in the last 30 min
     *                              (the support token's TTL — a proxy for "live now")
     */
    public record Security(
            long activeSupportSessions,
            long supportSessions7d,
            long breakGlass7d,
            long loginFailures24h,
            long passwordResets7d,
            List<SupportSession> recentSupportSessions,
            List<BreakGlassUse> recentBreakGlass
    ) {}

    public record SupportSession(
            String operator,
            String targetTenantCode,
            String targetDisplayName,
            OffsetDateTime startedAt,
            String reason,
            boolean active        // ended_at IS NULL AND expires_at > now()
    ) {}

    /**
     * A break-glass login: a successful {@code /auth/login} under OIDC mode (SSO
     * bypassed with the emergency credential), audited as oplog
     * {@code system / auth.breakGlass}.
     */
    public record BreakGlassUse(
            String operator,
            String tenantCode,
            OffsetDateTime usedAt,
            String clientIp
    ) {}
}
