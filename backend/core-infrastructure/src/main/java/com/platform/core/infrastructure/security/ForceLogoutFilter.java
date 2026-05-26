package com.platform.core.infrastructure.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.common.context.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ForceLogoutFilter(ForceLogoutService forceLogoutService, JwtDecoder jwtDecoder) {
        this.forceLogoutService = forceLogoutService;
        this.jwtDecoder = jwtDecoder;
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
        // / changeStatus / AdminAuthController.resetPassword all call
        // forceLogoutService.kickOut(<business-ulid>)). Reading JWT.subject here
        // would key by Keycloak UUID in OIDC mode → permanent kick-out miss.
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) {
            // Fallback for callers that haven't been through the context filter
            // (e.g. permit-all paths). subject equals business ULID in jwt mode.
            userId = jwt.getSubject();
        }
        if (userId == null || userId.isBlank()) {
            chain.doFilter(req, resp);
            return;
        }
        long kickAt = forceLogoutService.kickOutAt(userId);
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

    private void writeUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "UNAUTHORIZED");
        body.put("message", "Session terminated by administrator");
        try {
            objectMapper.writeValue(resp.getWriter(), body);
        } catch (JsonProcessingException e) {
            // last-ditch: write a plaintext fallback rather than swallow.
            resp.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Session terminated\"}");
        }
    }
}
