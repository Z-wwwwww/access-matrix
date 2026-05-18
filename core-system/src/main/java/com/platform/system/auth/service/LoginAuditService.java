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

    private final LoginLogMapper mapper;

    public LoginAuditService(LoginLogMapper mapper) {
        this.mapper = mapper;
    }

    @Async
    public void record(String userId, String identifier, String clientIp, String userAgent,
                       boolean success, String failureReason) {
        try {
            LoginLogEntity entity = new LoginLogEntity();
            entity.setId(IdGenerator.ulid());
            entity.setTenantId("default");
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
