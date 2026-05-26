package com.platform.core.infrastructure.web;

import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.security.OidcUserResolver;
import com.platform.core.infrastructure.security.rbac.DataScopeContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CoreRequestContextFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER  = "X-Trace-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String AUTH_HEADER   = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_TENANT = "default";
    private static final Locale DEFAULT_LOCALE = Locale.JAPAN;

    private final LocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
    {
        ((AcceptHeaderLocaleResolver) localeResolver).setDefaultLocale(DEFAULT_LOCALE);
    }

    /**
     * Optional — only present when {@code app.security.mode=oidc}. When
     * present we replace the JWT's {@code sub} (Keycloak UUID) with the
     * business {@code core_auth_user.id} so downstream RBAC / data scope
     * lookups hit the right row. See {@code OidcJitUserService}.
     */
    private final ObjectProvider<OidcUserResolver> oidcResolver;
    /**
     * Manual fallback decoder for the {@code permit-all} mode (where
     * Spring Security's oauth2-resource-server filter chain is NOT installed,
     * so {@link SecurityContextHolder} stays anonymous on every request).
     * Without this fallback, {@code RequestContext.userId} would be null
     * on every authenticated call in permit-all mode and every {@code /me}
     * endpoint would 401.
     */
    private final JwtDecoder jwtDecoder;

    public CoreRequestContextFilter(ObjectProvider<OidcUserResolver> oidcResolver,
                                    JwtDecoder jwtDecoder) {
        this.oidcResolver = oidcResolver;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String traceId = req.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        String tenantId = null;
        String userId = null;
        String username = null;
        Locale locale = null;

        Jwt jwt = currentJwt(req);
        if (jwt != null) {
            tenantId = jwt.getClaimAsString("tid");
            username = jwt.getClaimAsString("preferred_username");
            // OIDC 'locale' claim is the standard place (Keycloak emits it
            // from the user's UI language). Format may be "ja-JP" or "ja".
            String localeClaim = jwt.getClaimAsString("locale");
            if (localeClaim != null && !localeClaim.isBlank()) {
                locale = Locale.forLanguageTag(localeClaim.replace('_', '-'));
            }
            // Default: trust the JWT subject as-is. For HS256 (in-house
            // AdminAuthController.login) the sub IS already the business
            // ULID; for OIDC tokens it's the Keycloak UUID and the resolver
            // below translates it (or no-ops for non-OIDC tokens).
            userId = jwt.getSubject();
            OidcUserResolver resolver = oidcResolver.getIfAvailable();
            if (resolver != null) {
                String businessId = resolver.resolveBusinessUserId(jwt);
                if (businessId != null) userId = businessId;
            }
        }
        // No locale on the JWT (pre-auth / legacy / claim missing) → take it
        // from Accept-Language. The resolver falls back to ja_JP if nothing
        // sensible can be inferred.
        if (locale == null) {
            try {
                locale = localeResolver.resolveLocale(req);
            } catch (Exception e) {
                locale = DEFAULT_LOCALE;
            }
        }
        // Pre-auth requests (login / refresh) have no JWT — fall back to the
        // tenant header so the tenant interceptor + audit writes still get a
        // real value instead of writing every login as "default".
        if (tenantId == null || tenantId.isBlank()) {
            String header = req.getHeader(TENANT_HEADER);
            tenantId = (header == null || header.isBlank()) ? DEFAULT_TENANT : header.trim();
        }

        RequestContext.set(tenantId, userId, username, locale, traceId);
        MDC.put("traceId", traceId);
        if (tenantId != null) MDC.put("tenantId", tenantId);
        if (userId != null) MDC.put("userId", userId);
        resp.setHeader(TRACE_HEADER, traceId);

        try {
            chain.doFilter(req, resp);
        } finally {
            RequestContext.clear();
            DataScopeContext.clear();
            MDC.clear();
        }
    }

    /**
     * Get the JWT for this request, preferring the SecurityContext (set by
     * Spring Security's oauth2-resource-server filter in jwt / oidc modes)
     * and falling back to a manual decode of the Bearer header in permit-all
     * mode (where no resource-server filter is installed).
     *
     * <p>Returns null when no JWT is present (pre-auth paths like /auth/login,
     * /health) or when manual decode fails (invalid token — caller will see
     * RequestContext.userId == null and the controller's auth check kicks in).
     */
    private Jwt currentJwt(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return jat.getToken();
        }
        String header = req.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) return null;
        try {
            return jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
        } catch (Exception e) {
            // Bad token — leave RequestContext.userId null. The endpoint's
            // own auth requirements will reject it; we don't want to spam
            // logs on every drive-by malformed Bearer.
            return null;
        }
    }
}
