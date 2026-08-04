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

import java.time.OffsetDateTime;

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
            e.setTenantId(clamp(record.tenantId(), 64));
            e.setUserId(record.userId());
            e.setUsername(clamp(record.username(), 64));
            e.setModule(clamp(record.module(), 32));
            e.setAction(clamp(record.action(), 64));
            e.setTargetType(clamp(record.targetType(), 32));
            e.setTargetId(clamp(record.targetId(), 64));
            e.setRequestUri(clamp(record.requestUri(), 512));
            e.setMethod(clamp(record.method(), 8));
            e.setClientIp(clamp(record.clientIp(), 64));
            e.setUserAgent(clamp(record.userAgent(), 512));
            e.setRequestBody(record.requestBody());
            e.setSuccess(record.success());
            e.setErrorMsg(clamp(record.errorMsg(), 512));
            e.setErrorCode(record.errorCode());
            e.setCostMs(record.costMs());
            e.setCreateTime(OffsetDateTime.now());
            mapper.insert(e);
        } catch (Exception ex) {
            log.warn("OpLogService: failed to persist audit row action={}: {}",
                     record.action(), ex.getMessage());
        }
    }

    /**
     * Clamps a value to its {@code core_oplog} column width. Every producer of an
     * {@link OpLogRecord} feeds it request-controlled strings, and Postgres rejects
     * an over-long value outright ({@code value too long for type character
     * varying(512)}) — which the catch block above then swallows, so the audit row
     * disappears with only a WARN. {@code OpLogAspect} pre-truncates user-agent and
     * error message but passed {@code request.getRequestURI()} through raw, and the
     * pre-auth {@code recordAudit} helpers in InviteController /
     * PasswordResetController pre-truncate nothing at all. Clamping here — at the
     * single write point — makes it structurally impossible for any producer to
     * lose an audit row to a length overflow. A truncated audit row is strictly
     * better than a missing one.
     */
    private static String clamp(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }
}
