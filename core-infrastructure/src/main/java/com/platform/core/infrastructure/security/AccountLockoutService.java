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
     * Returns remaining lock seconds, or 0 if not locked.
     */
    public long remainingLockSeconds(String identifier) {
        if (!cfg.enabled()) return 0;
        String key = LOCK_PREFIX + normalize(identifier);
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? 0 : Math.max(0L, ttl);
    }

    public void recordFailure(String identifier) {
        if (!cfg.enabled()) return;
        String id = normalize(identifier);
        String failKey = FAIL_PREFIX + id;

        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redis.expire(failKey, cfg.window());
        }
        if (count != null && count >= cfg.maxFailures()) {
            redis.opsForValue().set(LOCK_PREFIX + id, "1", cfg.lockDuration());
            redis.delete(failKey);
        }
    }

    public void reset(String identifier) {
        if (!cfg.enabled()) return;
        String id = normalize(identifier);
        redis.delete(FAIL_PREFIX + id);
        redis.delete(LOCK_PREFIX + id);
    }

    public Duration lockDuration() {
        return cfg.lockDuration();
    }
}
