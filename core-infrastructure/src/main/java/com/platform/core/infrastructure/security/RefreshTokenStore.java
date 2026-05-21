package com.platform.core.infrastructure.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Component
public class RefreshTokenStore {

    public static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final String PREFIX = "auth:refresh:";
    private static final int TOKEN_BYTES = 32; // 256-bit
    private static final String SEP = "|";

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Mint a new refresh token bound to {@code (userId, tenantId)}. The stored
     * value carries {@code userId|tenantId|issuedAtSec} so the refresh path
     * can:
     * <ul>
     *   <li>Cross-check against {@link ForceLogoutService} without a separate
     *       Redis key.</li>
     *   <li>Look the user up under their real tenant, independent of whatever
     *       {@code X-Tenant-Id} header the refresh request carries — important
     *       when the MyBatis-Plus tenant interceptor is enabled.</li>
     * </ul>
     */
    public String issue(String userId, String tenantId) {
        byte[] buf = new byte[TOKEN_BYTES];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        String tid = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
        String value = userId + SEP + tid + SEP + Instant.now().getEpochSecond();
        redis.opsForValue().set(PREFIX + token, value, REFRESH_TTL);
        return token;
    }

    /**
     * Single-use rotation. Performs a Redis {@code GETDEL} (one round trip)
     * so concurrent rotations of the same token cannot both observe a
     * non-empty value — at most one caller wins, the rest get
     * {@link Optional#empty()}.
     */
    public Optional<Holder> rotate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String value = redis.opsForValue().getAndDelete(PREFIX + token);
        if (value == null) return Optional.empty();
        return Optional.of(Holder.parse(value));
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(PREFIX + token);
        }
    }

    /**
     * What {@link #rotate(String)} returns: bound user, tenant, and the moment
     * the refresh token was minted (Unix seconds).
     *
     * <p>Tolerates legacy 2-field entries ({@code userId|iat}) minted before
     * tenant-aware storage landed — those parse with {@code tenantId="default"}.
     */
    public record Holder(String userId, String tenantId, long issuedAtEpochSec) {

        static Holder parse(String stored) {
            if (stored == null) return new Holder(null, "default", 0L);
            String[] parts = stored.split("\\" + SEP, -1);
            if (parts.length == 1) {
                // Very-legacy: only userId stored, no metadata at all.
                return new Holder(parts[0], "default", 0L);
            }
            if (parts.length == 2) {
                // Legacy 2-field: userId|iat
                return new Holder(parts[0], "default", safeLong(parts[1]));
            }
            // Current 3-field: userId|tenantId|iat
            String tid = parts[1].isEmpty() ? "default" : parts[1];
            return new Holder(parts[0], tid, safeLong(parts[2]));
        }

        private static long safeLong(String s) {
            try { return Long.parseLong(s); }
            catch (NumberFormatException e) { return 0L; }
        }
    }
}
