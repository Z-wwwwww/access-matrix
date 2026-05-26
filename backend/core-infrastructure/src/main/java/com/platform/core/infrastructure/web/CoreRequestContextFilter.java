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

    public CoreRequestContextFilter(ObjectProvider<OidcUserResolver> oidcResolver) {
        this.oidcResolver = oidcResolver;
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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            tenantId = jwt.getClaimAsString("tid");
            username = jwt.getClaimAsString("preferred_username");
            // OIDC 'locale' claim is the standard place (Keycloak emits it
            // from the user's UI language). Format may be "ja-JP" or "ja".
            String localeClaim = jwt.getClaimAsString("locale");
            if (localeClaim != null && !localeClaim.isBlank()) {
                locale = Locale.forLanguageTag(localeClaim.replace('_', '-'));
            }
            // Default: trust the JWT subject as-is (legacy HS256 flow — sub
            // IS already the business ULID because we sign our own tokens).
            userId = jwt.getSubject();
            // When the OIDC resolver is on the classpath, sub is a Keycloak
            // UUID; convert it to the business id and provision the row
            // lazily on first login. Returning null = mapping failed; we
            // keep the raw sub so request still gets through (filters /
            // controllers can still 401/403 on missing permissions, but
            // we don't black-hole the request here).
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
}
