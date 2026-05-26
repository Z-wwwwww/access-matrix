package com.platform.core.infrastructure.security.rbac;

import com.platform.core.common.context.RequestContext;
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
 * <p>Scope is NEVER inlined into the JWT — we always load the current
 * permission set from the database (Caffeine-cached via
 * {@link UserPermissionsLookup}). Two reasons:
 *
 * <ul>
 *   <li><b>Live revocation</b>: an admin removing a role from a user must
 *       take effect on the user's NEXT request, not when their JWT expires
 *       15 minutes later. Inline scopes can't be revoked.</li>
 *   <li><b>OIDC mode compatibility</b>: an external IdP (Keycloak) emits
 *       the OIDC-standard {@code scope} claim ("openid profile email"),
 *       NOT our business permission codes ("user:read", "role:delete").
 *       Reading the token's scope would parse those as permission codes
 *       and the user would have exactly three useless authorities. Same
 *       reasoning applies if anyone ever switches to SAML / Azure AD —
 *       the token shape is the IdP's contract, not ours.</li>
 * </ul>
 *
 * <p>The legacy in-house {@code AdminAuthController} signs HS256 tokens
 * with {@code scope = "__compact__"} as a hint, but the value is now
 * informational — we look up regardless.
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
        Jwt jwt;
        if (auth instanceof JwtAuthenticationToken jat) {
            jwt = jat.getToken();
        } else {
            // permit-all mode: SecurityContext stays empty, decode the bearer header ourselves
            jwt = manuallyDecodeFromHeader();
            if (jwt == null) return Set.of();
        }
        UserPermissionsLookup lookup = lookupProvider.getIfAvailable();
        if (lookup == null) {
            log.warn("No UserPermissionsLookup bean registered — denying every permission check");
            return Set.of();
        }
        // Always load from DB. See class javadoc for why we ignore the JWT
        // scope claim entirely. Business user id comes from RequestContext
        // (translated from JWT.sub via OidcUserResolver in OIDC mode); fall
        // back to JWT subject only for the (legacy) HS256 jwt mode where
        // sub IS already the business ULID.
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) return Set.of();
        return lookup.loadUserPermissions(userId);
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
