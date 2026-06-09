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
    /** Tenant-wide kick (every user of a tenant) — set when a tenant is suspended. */
    private static final String TENANT_KEY_PREFIX = "core:auth:logout-tenant:";

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

    // ── Tenant-wide kick ────────────────────────────────────────────────────
    // A single key per tenant terminates EVERY user of that tenant at once
    // (O(1), no per-user enumeration). Set on tenant suspend; the filter rejects
    // any token of that tenant issued before the kick. Cleared on resume.

    /** Mark every user of {@code tenantCode} as force-logged-out at now. */
    public void kickOutTenant(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) return;
        long now = Instant.now().getEpochSecond();
        redis.opsForValue().set(TENANT_KEY_PREFIX + tenantCode, Long.toString(now), TTL);
    }

    /** @return the most recent tenant-wide kick timestamp (epoch second), or {@code 0}. */
    public long tenantKickOutAt(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) return 0L;
        String v = redis.opsForValue().get(TENANT_KEY_PREFIX + tenantCode);
        if (v == null || v.isBlank()) return 0L;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Clear the tenant-wide kick — used on tenant resume. */
    public void clearTenant(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) return;
        redis.delete(TENANT_KEY_PREFIX + tenantCode);
    }
}
