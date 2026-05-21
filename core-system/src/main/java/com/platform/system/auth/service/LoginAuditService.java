package com.platform.system.auth.service;

import com.platform.system.auth.entity.LoginLogEntity;
import com.platform.system.auth.mapper.LoginLogMapper;
import com.platform.core.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);
    private static final String DEFAULT_TENANT = "default";

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
            entity.setTenantId(tid);
            entity.setUserId(userId);
            entity.setIdentifier(identifier);
            entity.setClientIp(clientIp);
            entity.setUserAgent(userAgent);
            entity.setSuccess(success);
            entity.setFailureReason(failureReason);
            entity.setLoginTime(LocalDateTime.now());
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to record login audit: {}", e.getMessage());
        }
    }
}
