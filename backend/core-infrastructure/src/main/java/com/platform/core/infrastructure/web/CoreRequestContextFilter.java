package com.platform.core.infrastructure.web;

import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.security.OidcUserResolver;
import com.platform.core.infrastructure.security.rbac.DataScopeContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CoreRequestContextFilter.class);

    private static final String TRACE_HEADER  = "X-Trace-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String AUTH_HEADER   = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    // Fallback tenant when X-Tenant-Id is missing on a pre-auth request
    // (login / refresh / health). "demo" is the conventional dev / QA
    // tenant; in prod, callers should always set X-Tenant-Id explicitly
    // (subdomain routing in the SPA enforces this). Hard fail on missing
    // header is a follow-up — would currently break test fixtures that
    // don't bother setting it.
    private static final String DEFAULT_TENANT = "demo";
    private static final Locale DEFAULT_LOCALE = Locale.JAPAN;
    /**
     * Width of {@code core_domain_event.trace_id} — the narrowest column the
     * trace id is persisted into. The header is caller-supplied and completely
     * unbounded, and the outbox insert deliberately joins the business
     * transaction (that's the transactional-outbox guarantee), so an over-long
     * value does NOT degrade to a lost audit row: Postgres rejects the INSERT
     * ("value too long for type character varying(64)") and the whole state
     * change rolls back. Any gateway / APM agent that stamps a long correlation
     * id would make every event-publishing write fail with an opaque 500.
     * Clamp once at ingress so every consumer — RequestContext, the outbox row,
     * and the echoed response header — sees the same value.
     */
    private static final int TRACE_ID_MAX = 64;

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
            traceId = UUID.randomUUID().toString().replace("-", "");   // 32 chars
        } else if (traceId.length() > TRACE_ID_MAX) {
            traceId = traceId.substring(0, TRACE_ID_MAX);              // see TRACE_ID_MAX
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
            // below translates it (or no-ops for non-OIDC tokens). The
            // resolve is deferred until AFTER the tenant context is set —
            // see the RequestContext.set below.
            userId = jwt.getSubject();
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
            String rawHeader = req.getHeader(TENANT_HEADER);
            String normalised = normaliseTenant(rawHeader);
            if (normalised == null) {
                // Malformed is treated exactly like absent (→ DEFAULT_TENANT), which is
                // the contract this filter already had for a missing header. Log it so a
                // misconfigured caller is diagnosable instead of silently landing in the
                // default tenant; the value is request-controlled, so log its length
                // rather than the value itself.
                if (rawHeader != null && !rawHeader.isBlank()) {
                    log.warn("Rejected malformed {} header ({} chars) — not an RFC1035 label; "
                                    + "falling back to tenant '{}'",
                            TENANT_HEADER, rawHeader.length(), DEFAULT_TENANT);
                }
                normalised = DEFAULT_TENANT;
            }
            tenantId = normalised;
        }

        // Establish the tenant context BEFORE any tenant-scoped DB access.
        // The OIDC JIT resolver below issues tenant-scoped lookups
        // (findByKeycloakIdAndTenant / findByIdentifier); with row-level
        // tenant isolation enabled, the MyBatis interceptor injects
        // `WHERE tenant_id = <RequestContext.tenantId>`. If we resolved the
        // user BEFORE setting the context (as this filter used to), that
        // tenant was null and the interceptor fell back to "demo" — the
        // lookups then missed the real row under e.g. 'acme', JIT assumed
        // a brand-new user, and the INSERT collided on
        // uk_core_auth_user_tenant_username. userId here is still the KC sub;
        // we refresh it to the resolved business id once the resolver returns.
        RequestContext.set(tenantId, userId, username, locale, traceId);
        if (jwt != null) {
            OidcUserResolver resolver = oidcResolver.getIfAvailable();
            if (resolver != null) {
                String businessId = resolver.resolveBusinessUserId(jwt);
                // null = the resolver REFUSED this token (deleted / disabled user, or
                // one that completed the SSO→password reverse migration). Every one of
                // those branches documents its effect as "resolves to no business user
                // → no access" — so actually clear it. Leaving the Keycloak sub in
                // place made that untrue: the request carried on with userId = a 36-char
                // KC UUID, which is not a business identity but is not absent either.
                // Concretely it broke the audit trail — core_oplog.user_id is
                // character(26) and OpLogService swallows the insert failure, so the one
                // permission-free @OpLog endpoint (PUT /me/profile) recorded NOTHING for
                // such a request instead of a row with a null user. Verified against the
                // real DB that a KC UUID is rejected by that column and that it is
                // nullable. Clearing also removes the standing footgun of a permission-
                // free endpoint keying off a userId that resolves to no row.
                userId = businessId;
                RequestContext.set(tenantId, userId, username, locale, traceId);
            }
        }
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
     * RFC 1035 label — the shape a tenant id is required to have everywhere else:
     * {@code TenantDto.CreateRequest.tenantCode} validates it with this exact
     * pattern, {@code utils/tenant.js} rejects anything else client-side, and it is
     * what Keycloak allows for a realm name (the project's convention is
     * {@code tenant_id == realm name}).
     */
    private static final java.util.regex.Pattern TENANT_LABEL =
            java.util.regex.Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    /**
     * Normalise the {@code X-Tenant-Id} header, returning {@code null} for anything
     * that is not a legal tenant label so the caller falls back to the default.
     *
     * <p>This is a security boundary, not tidiness. {@code MybatisPlusConfig} feeds
     * the context tenant to {@code TenantLineInnerInterceptor} as
     * {@code new StringValue(tid)}, and MyBatis-Plus splices that into the SQL
     * <b>text</b> — it is not a bound parameter. jsqlparser's {@code StringValue}
     * does not escape (its constructor argument is literally named
     * {@code escapedValue}; {@code toString()} only wraps the value in quotes), so a
     * quote in the header terminates the literal: {@code X-Tenant-Id: x' OR '1'='1}
     * renders the scoping predicate as {@code tenant_id = 'x' OR '1'='1'}. On a
     * PRE-AUTH request — where this header IS the tenant, unlike an authenticated
     * request which takes it from the JWT {@code tid} claim — that turns
     * {@code AuthService.login}'s {@code findByIdentifier} scoping into a tautology,
     * so a login attempt nominally for a nonexistent tenant matches a user in ANY
     * tenant and then just needs their password. A bare {@code '} is worse-behaved
     * still: {@code new StringValue("'")} throws
     * {@code StringIndexOutOfBoundsException} out of the interceptor. Both verified
     * directly against the jsqlparser on the classpath.
     *
     * <p>Rejecting instead of escaping is deliberate: every legal tenant id already
     * satisfies {@link #TENANT_LABEL}, so nothing legitimate is lost, and the cap at
     * 63 chars also keeps the value inside the 64-wide {@code tenant_id} columns.
     */
    public static String normaliseTenant(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty() || !TENANT_LABEL.matcher(t).matches()) return null;
        return t;
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
