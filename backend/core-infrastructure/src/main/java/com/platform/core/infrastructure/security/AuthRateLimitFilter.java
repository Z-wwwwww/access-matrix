package com.platform.core.infrastructure.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /**
     * Upper bound on tracked client IPs. Beyond this Caffeine evicts the
     * least-recently-used entry — the evicted client simply starts with a
     * fresh (full) bucket, which is the same state it would have had if it
     * had never been seen. Sized so a realistic peak of distinct clients
     * fits comfortably while the map can never grow without limit.
     */
    private static final long MAX_TRACKED_IPS = 100_000L;

    private final AppSecurityProperties.RateLimit cfg;
    private final JsonMapper mapper;
    private final ClientIpResolver clientIpResolver;

    /**
     * Per-IP token buckets. Caffeine rather than a plain {@code ConcurrentHashMap}
     * because nothing ever removed entries from that map: every distinct client IP
     * that touched an {@code /auth/} path left a {@link Bucket} behind for the life
     * of the JVM. On a public-facing deployment that grows unboundedly with normal
     * traffic, and an attacker rotating source addresses (or spoofed
     * {@code X-Forwarded-For} values when {@code trust-forwarded-headers=true})
     * turns it into a cheap memory-exhaustion vector against the very filter that
     * is supposed to be the throttle.
     *
     * <p>Eviction is safe for the security property this filter provides: a bucket
     * only ever holds <em>consumed</em> tokens, so dropping it can never make an
     * IP <em>more</em> throttled — and entries are kept for at least a full refill
     * period after their last use, by which point the bucket would have refilled to
     * full anyway. Idle-expiry therefore discards nothing that was still limiting.
     */
    private final LoadingCache<String, Bucket> buckets;

    @Autowired
    public AuthRateLimitFilter(AppSecurityProperties props, JsonMapper mapper,
                               ClientIpResolver clientIpResolver) {
        this.cfg = props.rateLimit();
        this.mapper = mapper;
        this.clientIpResolver = clientIpResolver;
        Duration idleTtl = cfg.refillPeriod() == null ? Duration.ofMinutes(1) : cfg.refillPeriod();
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_IPS)
                .expireAfterAccess(idleTtl)
                .build(k -> newBucket());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return !cfg.enabled() || uri == null || !uri.contains("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIpResolver.resolve(req);
        // Servlet containers may hand back a null peer for an already-closed
        // connection; bucket them together rather than NPE inside the cache.
        Bucket bucket = buckets.get(ip == null || ip.isBlank() ? "unknown" : ip);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1L);

        if (probe.isConsumed()) {
            resp.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(req, resp);
        } else {
            long retryAfterSec = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            resp.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            resp.setHeader("Retry-After", String.valueOf(retryAfterSec));
            resp.setHeader("X-RateLimit-Remaining", "0");
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(mapper.writeValueAsString(JsonResult.error(ErrorCode.TOO_MANY_REQUESTS)));
        }
    }

    /**
     * How many client IPs are currently tracked. Package-private — lets a test
     * assert the store stays bounded, which is the whole point of the cache.
     */
    long trackedIpCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    private Bucket newBucket() {
        Duration refill = cfg.refillPeriod();
        Bandwidth limit = Bandwidth.classic(cfg.requestsPerMinute(),
                Refill.intervally(cfg.requestsPerMinute(), refill));
        return Bucket.builder().addLimit(limit).build();
    }
}
