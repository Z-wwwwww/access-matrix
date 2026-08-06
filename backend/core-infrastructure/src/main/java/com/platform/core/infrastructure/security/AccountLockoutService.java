package com.platform.core.infrastructure.security;

import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class AccountLockoutService {

    private static final String FAIL_PREFIX = "auth:fail:";
    private static final String LOCK_PREFIX = "auth:lock:";
    /**
     * Only reached if a caller hands us a blank tenant; {@code CoreRequestContextFilter}
     * already substitutes its own fallback, so today nothing gets here through HTTP.
     * Kept in step with that filter's constant regardless — the tenant literally named
     * {@code default} stopped existing when V25 renamed it, so the old value would key
     * lockout counters under a phantom while every other component used {@code demo}.
     */
    private static final String DEFAULT_TENANT = "demo";

    private final StringRedisTemplate redis;
    private final AppSecurityProperties.Lockout cfg;

    public AccountLockoutService(StringRedisTemplate redis, AppSecurityProperties props) {
        this.redis = redis;
        this.cfg = props.lockout();
    }

    private static String normalize(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase();
    }

    /**
     * Tenant-scoped key. Multi-tenant deployments share the username space —
     * "admin" in tenant A and "admin" in tenant B must not influence each other,
     * otherwise a brute-force attack on one tenant locks every tenant's "admin"
     * out. Falls back to {@link #DEFAULT_TENANT} when no tenant has been resolved.
     */
    private static String keyOf(String prefix, String tenantId, String identifier) {
        String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId;
        return prefix + tid + ":" + normalize(identifier);
    }

    /** Returns remaining lock seconds, or 0 if not locked. */
    public long remainingLockSeconds(String tenantId, String identifier) {
        if (!cfg.enabled()) return 0;
        Long ttl = redis.getExpire(keyOf(LOCK_PREFIX, tenantId, identifier), TimeUnit.SECONDS);
        return ttl == null ? 0 : Math.max(0L, ttl);
    }

    public void recordFailure(String tenantId, String identifier) {
        if (!cfg.enabled()) return;
        String failKey = keyOf(FAIL_PREFIX, tenantId, identifier);

        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redis.expire(failKey, cfg.window());
        }
        if (count != null && count >= cfg.maxFailures()) {
            redis.opsForValue().set(keyOf(LOCK_PREFIX, tenantId, identifier), "1", cfg.lockDuration());
            redis.delete(failKey);
        }
    }

    public void reset(String tenantId, String identifier) {
        if (!cfg.enabled()) return;
        redis.delete(keyOf(FAIL_PREFIX, tenantId, identifier));
        redis.delete(keyOf(LOCK_PREFIX, tenantId, identifier));
    }

    public Duration lockDuration() {
        return cfg.lockDuration();
    }
}
