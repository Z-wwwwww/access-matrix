package com.platform.system.rbac.service;

import com.platform.core.common.id.IdGenerator;
import com.platform.core.infrastructure.audit.OpLogRecord;
import com.platform.core.infrastructure.audit.OpLogSink;
import com.platform.system.rbac.entity.OpLogEntity;
import com.platform.system.rbac.mapper.OpLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Persists audit log rows asynchronously. {@code @EnableAsync} is already on
 * the application class, so {@code @Async} schedules the insert onto Spring's
 * default task executor and the controller's response is never blocked by
 * the audit write.
 */
@Service
public class OpLogService implements OpLogSink {

    private static final Logger log = LoggerFactory.getLogger(OpLogService.class);

    private final OpLogMapper mapper;

    public OpLogService(OpLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Async
    public void record(OpLogRecord record) {
        try {
            OpLogEntity e = new OpLogEntity();
            e.setId(IdGenerator.ulid());
            e.setTenantId(record.tenantId());
            e.setUserId(record.userId());
            e.setUsername(record.username());
            e.setModule(record.module());
            e.setAction(record.action());
            e.setTargetType(record.targetType());
            e.setTargetId(record.targetId());
            e.setRequestUri(record.requestUri());
            e.setMethod(record.method());
            e.setClientIp(record.clientIp());
            e.setUserAgent(record.userAgent());
            e.setRequestBody(record.requestBody());
            e.setSuccess(record.success());
            e.setErrorMsg(record.errorMsg());
            e.setCostMs(record.costMs());
            e.setCreateTime(LocalDateTime.now());
            mapper.insert(e);
        } catch (Exception ex) {
            log.warn("OpLogService: failed to persist audit row action={}: {}",
                     record.action(), ex.getMessage());
        }
    }
}
