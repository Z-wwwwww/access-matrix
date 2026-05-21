package com.platform.core.infrastructure.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Hard-kill switch for an individual user's active sessions.
 *
 * <p>An entry is a Unix epoch second written into Redis under the key
 * {@code core:auth:logout:{userId}}. Every permission check thereafter
 * compares the caller's JWT {@code iat} claim to this timestamp; if the
 * token was issued before the kick, the request is rejected with 401.
 *
 * <p>TTL is 8 d — must outlive the longest refresh token ({@code REFRESH_TTL}
 * is 7 d) so a kicked-out user can't mint a fresh access token via /auth/refresh
 * after the kick entry has expired.
 */
@Component
public class ForceLogoutService {

    public static final Duration TTL = Duration.ofDays(8);
    private static final String KEY_PREFIX = "core:auth:logout:";

    private final StringRedisTemplate redis;

    public ForceLogoutService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Mark the user as force-logged-out at now. */
    public void kickOut(String userId) {
        if (userId == null || userId.isBlank()) return;
        long now = Instant.now().getEpochSecond();
        redis.opsForValue().set(KEY_PREFIX + userId, Long.toString(now), TTL);
    }

    /**
     * @return the timestamp (epoch second) of the most recent kick-out for
     *         this user, or {@code 0} if none exists.
     */
    public long kickOutAt(String userId) {
        if (userId == null || userId.isBlank()) return 0L;
        String v = redis.opsForValue().get(KEY_PREFIX + userId);
        if (v == null || v.isBlank()) return 0L;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Clear the kick — used by tests / admin "re-enable" flows. */
    public void clear(String userId) {
        if (userId == null || userId.isBlank()) return;
        redis.delete(KEY_PREFIX + userId);
    }
}
