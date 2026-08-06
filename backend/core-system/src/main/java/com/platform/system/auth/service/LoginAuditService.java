package com.platform.system.auth.service;

import com.platform.system.auth.entity.LoginLogEntity;
import com.platform.system.auth.mapper.LoginLogMapper;
import com.platform.core.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);
    /**
     * Same defensive fallback as {@code AccountLockoutService}; unreachable through
     * HTTP because {@code CoreRequestContextFilter} resolves a tenant first. Kept in
     * step with it: {@code default} is not a tenant any more (V25 renamed it to
     * {@code demo}), so the old value would have filed audit rows under a tenant no
     * scoped query can see.
     */
    private static final String DEFAULT_TENANT = "demo";

    private final LoginLogMapper mapper;

    public LoginAuditService(LoginLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Persist a login attempt audit row. Callers must pass {@code tenantId}
     * explicitly — the @Async dispatch runs on a worker thread that does not
     * inherit {@code RequestContext}'s ThreadLocal.
     */
    @Async
    public void record(String tenantId, String userId, String identifier, String clientIp,
                       String userAgent, boolean success, String failureReason) {
        String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId;
        try {
            LoginLogEntity entity = new LoginLogEntity();
            entity.setId(IdGenerator.ulid());
            entity.setTenantId(clamp(tid, 64));
            entity.setUserId(userId);
            entity.setIdentifier(clamp(identifier, 128));
            entity.setClientIp(clamp(clientIp, 64));
            entity.setUserAgent(clamp(userAgent, 512));
            entity.setSuccess(success);
            entity.setFailureReason(clamp(failureReason, 128));
            entity.setLoginTime(OffsetDateTime.now());
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to record login audit: {}", e.getMessage());
        }
    }

    /**
     * Clamps a value to its {@code core_auth_login_log} column width.
     *
     * <p>Every argument here is attacker-reachable on an UNAUTHENTICATED request:
     * {@code identifier} is {@code LoginRequest.username}, which carries only
     * {@code @NotBlank} (no {@code @Size}); {@code userAgent} is the raw
     * User-Agent header; {@code tid} is the raw X-Tenant-Id header. Postgres
     * rejects an over-long value outright (verified against the real DB for
     * identifier/129, user_agent/513 and tenant_id/65), and the catch above
     * swallows it so the failed-login row simply never lands — leaving the
     * account-lockout counter (Redis) incremented with nothing in the audit
     * trail, i.e. a trivially self-service way to brute-force without a paper
     * trail. Clamping keeps the row; a truncated audit row beats a missing one.
     */
    private static String clamp(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }
}
