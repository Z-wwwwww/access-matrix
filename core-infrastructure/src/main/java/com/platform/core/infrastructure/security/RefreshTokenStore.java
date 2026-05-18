package com.platform.core.infrastructure.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Component
public class RefreshTokenStore {

    public static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final String PREFIX = "auth:refresh:";
    private static final int TOKEN_BYTES = 32; // 256-bit

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String issue(String userId) {
        byte[] buf = new byte[TOKEN_BYTES];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        redis.opsForValue().set(PREFIX + token, userId, REFRESH_TTL);
        return token;
    }

    /**
     * Single-use rotation. Returns the userId on success and atomically deletes the token.
     */
    public Optional<String> rotate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String key = PREFIX + token;
        String userId = redis.opsForValue().get(key);
        if (userId == null) return Optional.empty();
        Boolean deleted = redis.delete(key);
        if (!Boolean.TRUE.equals(deleted)) return Optional.empty();
        return Optional.of(userId);
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(PREFIX + token);
        }
    }
}
