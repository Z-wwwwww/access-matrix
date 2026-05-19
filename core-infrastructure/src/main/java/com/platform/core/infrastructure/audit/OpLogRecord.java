package com.platform.core.infrastructure.audit;

/**
 * Plain DTO carrying a single audit log entry across the
 * {@code OpLogAspect → OpLogSink} boundary.
 */
public record OpLogRecord(
        String tenantId,
        String userId,
        String username,
        String module,
        String action,
        String targetType,
        String targetId,
        String requestUri,
        String method,
        String clientIp,
        String userAgent,
        String requestBody,
        boolean success,
        String errorMsg,
        int costMs
) {}
