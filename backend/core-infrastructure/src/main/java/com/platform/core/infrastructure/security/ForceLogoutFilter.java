package com.platform.core.infrastructure.security;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * Cross-cuts every authenticated request and enforces administrative
 * force-logout regardless of whether the target endpoint is annotated
 * with {@code @RequiresPermission}.
 *
 * <p>Why a filter (and not just the AOP aspect): endpoints like
 * {@code GET /menu/me}, {@code /permission/me}, {@code /user/me} carry only
 * "logged-in" as their requirement and are not annotated, so the aspect-based
 * check used to skip them entirely. A kicked-out user could keep pulling
 * their menu and permission set until the JWT expired — defeating the point.
 *
 * <p>Resolution order mirrors {@code PermissionResolver}: prefer a JWT that
 * Spring Security has already validated, otherwise decode the bearer token
 * manually so permit-all mode (where the resource-server filter is absent)
 * still gets the check.
 *
 * <p>Pre-auth paths ({@code /auth/login}, {@code /auth/refresh}, health,
 * actuator, swagger) generally have no JWT and pass through unchanged. The
 * refresh endpoint additionally re-runs the kick check inside the service so
 * a stolen refresh token cannot mint a new access token.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ForceLogoutFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ForceLogoutFilter.class);

    private final ForceLogoutService forceLogoutService;
    private final JwtDecoder jwtDecoder;
    /** Jackson 3, injected — the app registers no Jackson 2 bean. */
    private final JsonMapper mapper;

    public ForceLogoutFilter(ForceLogoutService forceLogoutService, JwtDecoder jwtDecoder,
                             JsonMapper mapper) {
        this.forceLogoutService = forceLogoutService;
        this.jwtDecoder = jwtDecoder;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        Jwt jwt = currentJwt(req);
        if (jwt == null) {
            chain.doFilter(req, resp);
            return;
        }
        // CoreRequestContextFilter runs BEFORE this filter (Ordered chain) and
        // has already converted JWT subject → business ULID via OidcUserResolver.
        // Force-logout keys are stored by business ULID (UserAdminService.delete
        // / changeStatus / resetPassword all call
        // forceLogoutService.kickOut(<business-ulid>)). Reading JWT.subject here
        // would key by Keycloak UUID in OIDC mode → permanent kick-out miss.
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) {
            // Fallback for callers that haven't been through the context filter
            // (e.g. permit-all paths). subject equals business ULID in jwt mode.
            userId = jwt.getSubject();
        }
        // Reject if the token predates EITHER a per-user kick (disable / delete /
        // force-logout) OR a tenant-wide kick (tenant suspended). The tenant kick
        // is keyed by tenant_id = tenant_code; RequestContext.tenantId() was set by
        // CoreRequestContextFilter (runs before this filter) from the JWT tid claim.
        long userKickAt = (userId == null || userId.isBlank()) ? 0L : forceLogoutService.kickOutAt(userId);
        long tenantKickAt = forceLogoutService.tenantKickOutAt(RequestContext.tenantId());
        long kickAt = Math.max(userKickAt, tenantKickAt);
        if (kickAt <= 0) {
            chain.doFilter(req, resp);
            return;
        }
        Instant iat = jwt.getIssuedAt();
        // iat missing → token can't prove it postdates the kick; safest to reject.
        long iatSec = iat == null ? 0L : iat.getEpochSecond();
        // <= rather than < — JWT iat is second-precision; a kick that lands
        // in the same second as token issue should still terminate the session.
        if (iatSec <= kickAt) {
            writeUnauthorized(resp);
            return;
        }
        chain.doFilter(req, resp);
    }

    private Jwt currentJwt(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return jat.getToken();
        }
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            return jwtDecoder.decode(header.substring(7));
        } catch (Exception e) {
            log.debug("Manual JWT decode failed in ForceLogoutFilter: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Same shape as every other response in the app: the {@link JsonResult}
     * envelope, an {@code int} code from {@link ErrorCode}, and an i18n key in
     * {@code msg} for the frontend's {@code localizeError} to resolve.
     *
     * <p>This used to hand-build {@code {"code":"UNAUTHORIZED","message":…}} — a
     * String where the contract says int, and {@code message} where every client
     * reads {@code msg}. {@code AuthRateLimitFilter}, the only other filter that
     * short-circuits with a body, already did it the contract way; this one was
     * the outlier. The visible cost: {@code request.js} surfaces
     * {@code error.response.data?.msg}, so the administrator's reason for the kick
     * resolved to {@code undefined} and the user got the generic
     * "リクエストに失敗しました" instead.
     */
    private void writeUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(mapper.writeValueAsString(
                JsonResult.error(ErrorCode.UNAUTHORIZED.code(), "error.auth.sessionTerminated")));
    }
}
