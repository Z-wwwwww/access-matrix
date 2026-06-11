package com.platform.core.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP for rate-limiting and audit, treating
 * {@code X-Forwarded-For} / {@code X-Real-IP} as <b>untrusted by default</b>.
 *
 * <h3>Why this exists</h3>
 * <p>Those headers are set by the client. Honoring them unconditionally let an
 * attacker rotate {@code X-Forwarded-For} per request to (a) bypass the per-IP
 * {@link AuthRateLimitFilter} entirely (each spoofed value is a fresh bucket,
 * so brute-force / password-spray on {@code /auth/*} is unthrottled) and
 * (b) forge the IP recorded in {@code core_oplog}, {@code core_auth_login_log}
 * and the break-glass alert email — defeating that alert's whole point of
 * showing "from IP &lt;not you&gt;". The old code also took the <em>leftmost</em>
 * XFF token, which is exactly the client-controlled part.
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li>{@code app.security.trust-forwarded-headers=false} (default) — ignore
 *       the headers entirely and use the real socket peer
 *       ({@code getRemoteAddr}). Correct + safe for a direct-exposure deploy.</li>
 *   <li>{@code true} — only set this when the app sits behind a reverse proxy
 *       you control that <em>overwrites</em> the header. We then take the
 *       <em>rightmost</em> XFF entry (the hop the nearest trusted proxy
 *       observed; the leftmost entries are still attacker-supplied), falling
 *       back to {@code X-Real-IP} then {@code getRemoteAddr}. For a chain of
 *       multiple proxies, prefer binding actuator/management to an internal
 *       network and configuring a hop-aware proxy that strips inbound XFF.</li>
 * </ul>
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwarded;

    public ClientIpResolver(
            @Value("${app.security.trust-forwarded-headers:false}") boolean trustForwarded) {
        this.trustForwarded = trustForwarded;
    }

    public String resolve(HttpServletRequest req) {
        if (req == null) return null;
        if (trustForwarded) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                // Rightmost = appended by our nearest trusted proxy; leftmost is
                // whatever the client claimed and must never be trusted.
                int lastComma = xff.lastIndexOf(',');
                return (lastComma >= 0 ? xff.substring(lastComma + 1) : xff).trim();
            }
            String xrip = req.getHeader("X-Real-IP");
            if (xrip != null && !xrip.isBlank()) return xrip.trim();
        }
        return req.getRemoteAddr();
    }
}
