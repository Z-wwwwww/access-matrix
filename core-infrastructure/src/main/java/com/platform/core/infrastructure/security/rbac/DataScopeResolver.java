package com.platform.core.infrastructure.security.rbac;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Returns the {@link DataScopeDecision} for the current request.
 *
 * <p>Same resolution order as {@code PermissionResolver}:
 * <ol>
 *   <li>{@link SecurityContextHolder} has a {@link JwtAuthenticationToken}
 *       (jwt mode) → use its subject claim.</li>
 *   <li>Otherwise (permit-all mode) decode {@code Authorization: Bearer ...}
 *       manually.</li>
 *   <li>No token → return {@link DataScopeDecision#empty(String)}.</li>
 * </ol>
 *
 * <p>The actual decision-load is delegated to a {@link UserDataScopeLookup}
 * bean (implemented in core-system, Caffeine-cached).
 */
@Component
public class DataScopeResolver {

    private static final Logger log = LoggerFactory.getLogger(DataScopeResolver.class);

    private final JwtDecoder jwtDecoder;
    private final ObjectProvider<UserDataScopeLookup> lookupProvider;

    public DataScopeResolver(JwtDecoder jwtDecoder,
                             ObjectProvider<UserDataScopeLookup> lookupProvider) {
        this.jwtDecoder = jwtDecoder;
        this.lookupProvider = lookupProvider;
    }

    public DataScopeDecision currentDecision() {
        String userId = currentUserId();
        if (userId == null) return DataScopeDecision.empty(null);
        UserDataScopeLookup lookup = lookupProvider.getIfAvailable();
        if (lookup == null) {
            log.warn("No UserDataScopeLookup bean registered — returning empty decision for {}", userId);
            return DataScopeDecision.empty(userId);
        }
        return lookup.loadDecision(userId);
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return jat.getToken().getSubject();
        }
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            return jwt.getSubject();
        } catch (Exception e) {
            log.debug("Manual JWT decode failed in DataScopeResolver: {}", e.getMessage());
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) return sra.getRequest();
        return null;
    }
}
