package com.platform.system.platform.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.infrastructure.security.JwtIssuer;
import com.platform.system.platform.dto.TenantDto;
import com.platform.system.platform.entity.TenantEntity;
import com.platform.system.platform.mapper.TenantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mints short-lived "support session" JWTs that let a platform-ops user
 * act with a target tenant's SUPER_ADMIN authority for incident triage.
 *
 * <h3>Audit posture</h3>
 * Every minted token carries two signals:
 * <ol>
 *   <li><b>{@code preferred_username = "[support] <ops>"}</b> — the
 *       visible audit prefix. Anything written downstream that touches
 *       {@code RequestContext.username()} (most notably {@code core_oplog})
 *       lands with this prefix, so a routine query "what did ops do in
 *       acme today?" surfaces these immediately.</li>
 *   <li><b>{@code act} claim</b> (RFC 8693 actor claim) — structured
 *       record of the original ops user (sub, tid, username, session_id,
 *       reason). For forensic / compliance digests we can decode this
 *       from the JWT and join against the platform-side oplog row.</li>
 * </ol>
 *
 * <p>The platform-side oplog row is written by the {@code @OpLog} aspect
 * on the impersonate-start controller call (action =
 * {@code tenant.impersonate.start}), with the reason in {@code request_body}
 * and the target tenant in {@code target_id} — so the "I started a
 * session at HH:MM for OS-1234" half of the audit trail is captured
 * before the support work even begins.
 *
 * <h3>Why we use the target's SUPER_ADMIN as the JWT {@code sub}</h3>
 * <p>The alternative (creating a shadow {@code __support__} user per
 * tenant) is more isolated but adds DDL + a confusing extra row. Using
 * the existing SUPER_ADMIN keeps the user table clean; the {@code act}
 * claim + username prefix are what actually carry the "this isn't really
 * the tenant admin doing this" signal.
 *
 * <h3>Known limitations (v1)</h3>
 * <ul>
 *   <li>FULL mode only — no READ_ONLY enforcement yet. Audit is the
 *       sole protection. Tracked as a follow-up.</li>
 *   <li>No server-side session list / revoke. Tokens expire naturally
 *       in 30 min; clients terminate by discarding the token.</li>
 *   <li>Built-in tenants (system, demo) refused — there's no operational
 *       reason to impersonate them and accidents here cost more.</li>
 * </ul>
 */
@Service
public class TenantImpersonationService {

    private static final Logger log = LoggerFactory.getLogger(TenantImpersonationService.class);

    /** tenant codes reserved by the project — same set as TenantAdminService.RESERVED_CODES. */
    private static final Set<String> RESERVED_CODES = Set.of("system", "demo");

    /** Name of the auto-seeded super-admin role — keep in sync with RbacSeederService. */
    private static final String SUPER_ROLE_NAME = "Super Administrator";

    /** Support session lifetime. Long enough for normal triage, short enough that a leaked token expires before useful reuse. */
    private static final Duration SUPPORT_SESSION_TTL = Duration.ofMinutes(30);

    private final TenantMapper tenantMapper;
    private final JdbcTemplate jdbc;
    private final JwtIssuer jwtIssuer;

    public TenantImpersonationService(TenantMapper tenantMapper,
                                      JdbcTemplate jdbc,
                                      JwtIssuer jwtIssuer) {
        this.tenantMapper = tenantMapper;
        this.jdbc = jdbc;
        this.jwtIssuer = jwtIssuer;
    }

    public TenantDto.SupportSessionResponse startSession(String tenantRegistryId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "reason is required — every support session must be justified");
        }
        TenantEntity row = tenantMapper.selectById(tenantRegistryId);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + tenantRegistryId);
        }
        if (RESERVED_CODES.contains(row.getTenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in tenant '" + row.getTenantCode() + "' cannot be impersonated");
        }

        // Look up the target tenant's SUPER_ADMIN role by name (per-tenant ULID).
        String roleId;
        try {
            roleId = jdbc.queryForObject(
                    "SELECT id FROM core_rbac_role "
                            + " WHERE tenant_id = ? AND name = ? AND is_built_in = 1 AND mark = 1",
                    String.class, row.getTenantCode(), SUPER_ROLE_NAME);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "Tenant '" + row.getTenantCode() + "' has no built-in SUPER_ADMIN role — "
                            + "was it created before the RBAC seeder existed?");
        }

        // Pick the oldest active SUPER_ADMIN user — deterministic across
        // retries, and "oldest" is usually the auto-provisioned admin from
        // tenant creation rather than a later-added co-admin.
        String userId;
        try {
            userId = jdbc.queryForObject(
                    "SELECT u.id FROM core_auth_user u "
                            + "  JOIN core_rbac_user_role ur ON ur.user_id = u.id "
                            + " WHERE ur.tenant_id = ? AND ur.role_id = ? "
                            + "   AND ur.mark = 1 AND u.mark = 1 "
                            + " ORDER BY u.create_time, u.id LIMIT 1",
                    String.class, row.getTenantCode(), roleId);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "Tenant '" + row.getTenantCode() + "' has no SUPER_ADMIN user to impersonate — "
                            + "operator must provision one first");
        }

        String sessionId = UUID.randomUUID().toString();
        String actorUserId  = RequestContext.userId();
        String actorTenant  = RequestContext.tenantId();
        // RequestContext doesn't store username on the context object the way
        // it does userId/tenantId — pull from the .current() snapshot.
        RequestContext ctx  = RequestContext.current();
        String actorUsername = ctx == null ? null : ctx.getUsername();

        // Linked HashMap to preserve serialisation order on inspection.
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("sub", actorUserId);
        act.put("tid", actorTenant);
        act.put("username", actorUsername);
        act.put("session_id", sessionId);
        act.put("reason", reason);
        act.put("mode", "FULL");

        String prefixedUsername = "[support] " + (actorUsername == null ? "ops" : actorUsername);

        // tenant:* gives the impersonating session full business authority in
        // the target tenant. READ_ONLY mode (a separate scope string) is the
        // follow-up; v1 relies on audit for accountability.
        JwtIssuer.TokenIssue issued = jwtIssuer.issueSupportSession(
                userId, row.getTenantCode(), prefixedUsername,
                "tenant:*", List.of(roleId),
                act, SUPPORT_SESSION_TTL);

        log.info("[support] minted session for ops={} → tenant={} (session={}, ttl={}min, reason='{}')",
                actorUsername, row.getTenantCode(), sessionId, SUPPORT_SESSION_TTL.toMinutes(), reason);

        return new TenantDto.SupportSessionResponse(
                issued.token(),
                sessionId,
                row.getTenantCode(),
                row.getDisplayName(),
                issued.expiresAt().toString(),
                issued.expiresInSec());
    }
}
