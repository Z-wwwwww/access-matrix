package com.platform.core.infrastructure.security;

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
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AppSecurityProperties.RateLimit cfg;
    private final JsonMapper mapper;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimitFilter(AppSecurityProperties props, JsonMapper mapper) {
        this.cfg = props.rateLimit();
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return !cfg.enabled() || uri == null || !uri.contains("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(req);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());
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

    private Bucket newBucket() {
        Duration refill = cfg.refillPeriod();
        Bandwidth limit = Bandwidth.classic(cfg.requestsPerMinute(),
                Refill.intervally(cfg.requestsPerMinute(), refill));
        return Bucket.builder().addLimit(limit).build();
    }

    static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xrip = req.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) return xrip.trim();
        return req.getRemoteAddr();
    }
}
