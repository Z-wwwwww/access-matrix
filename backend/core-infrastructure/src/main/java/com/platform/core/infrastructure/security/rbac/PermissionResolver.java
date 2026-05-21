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

import java.util.Set;

/**
 * Converts the current HTTP request into the caller's permission-code set.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@link SecurityContextHolder} holds a {@link JwtAuthenticationToken} (jwt mode)
 *       → read the {@code scope} claim.</li>
 *   <li>Otherwise (permit-all mode) decode the {@code Authorization: Bearer ...}
 *       header manually via {@link JwtDecoder}.</li>
 *   <li>Neither → return empty set (caller will be denied unless the endpoint is unguarded).</li>
 * </ol>
 *
 * <p>When the {@code scope} claim is the literal {@code "__compact__"} the user has
 * too many permissions to fit in the JWT and we delegate to
 * {@link UserPermissionsLookup} (typically Caffeine-cached, see Stage 1 plan).
 */
@Component
public class PermissionResolver {

    static final String COMPACT_MARKER = "__compact__";
    private static final Logger log = LoggerFactory.getLogger(PermissionResolver.class);

    private final JwtDecoder jwtDecoder;
    private final ObjectProvider<UserPermissionsLookup> lookupProvider;

    public PermissionResolver(JwtDecoder jwtDecoder,
                              ObjectProvider<UserPermissionsLookup> lookupProvider) {
        this.jwtDecoder = jwtDecoder;
        this.lookupProvider = lookupProvider;
    }

    public Set<String> resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return fromJwt(jat.getToken());
        }
        Jwt manual = manuallyDecodeFromHeader();
        if (manual != null) {
            return fromJwt(manual);
        }
        return Set.of();
    }

    private Set<String> fromJwt(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) return Set.of();
        if (COMPACT_MARKER.equals(scope)) {
            UserPermissionsLookup lookup = lookupProvider.getIfAvailable();
            if (lookup == null) {
                log.warn("scope=__compact__ but no UserPermissionsLookup bean registered; denying");
                return Set.of();
            }
            return lookup.loadUserPermissions(jwt.getSubject());
        }
        return Set.of(scope.trim().split("\\s+"));
    }

    private Jwt manuallyDecodeFromHeader() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            return jwtDecoder.decode(header.substring(7));
        } catch (Exception e) {
            log.debug("Manual JWT decode failed: {}", e.getMessage());
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}
