package com.platform.core.infrastructure.security.rbac;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.security.PermissionMatcher;
import com.platform.core.common.security.RequiresPermission;
import com.platform.core.infrastructure.security.ForceLogoutService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Set;

/**
 * Enforces {@link RequiresPermission} on controller / service methods.
 *
 * <p>Order = 10 → runs <em>before</em> {@code OpLogAspect} (order 50) so a
 * denied request never produces a "success" audit row.
 *
 * <p>Per-check pipeline:
 * <ol>
 *   <li>Validate annotation has at least one required permission.</li>
 *   <li>Resolve caller's permission set via {@link PermissionResolver}.</li>
 *   <li>Cross-check the caller's JWT {@code iat} against
 *       {@link ForceLogoutService} so admin-issued kick-outs take effect
 *       within milliseconds.</li>
 *   <li>Match required permissions with wildcard-aware
 *       {@link PermissionMatcher}.</li>
 * </ol>
 */
@Aspect
@Component
@Order(10)
public class PermissionAspect {

    private final PermissionResolver resolver;
    private final ForceLogoutService forceLogoutService;
    private final JwtDecoder jwtDecoder;

    public PermissionAspect(PermissionResolver resolver,
                            ForceLogoutService forceLogoutService,
                            JwtDecoder jwtDecoder) {
        this.resolver = resolver;
        this.forceLogoutService = forceLogoutService;
        this.jwtDecoder = jwtDecoder;
    }

    @Before("@annotation(annotation)")
    public void check(RequiresPermission annotation) {
        String[] required = annotation.anyOf().length > 0
                ? annotation.anyOf()
                : new String[]{annotation.value()};

        if (required.length == 0 || (required.length == 1 && (required[0] == null || required[0].isBlank()))) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "@RequiresPermission must specify value() or anyOf()");
        }

        // Force-logout precedes permission check so a kicked-out admin can't
        // self-restore by calling the role/permission endpoints in flight.
        enforceForceLogout();

        Set<String> userPerms = resolver.resolve();
        if (PermissionMatcher.matchesAny(userPerms, required)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "Missing required authority: " + String.join(",", required));
    }

    private void enforceForceLogout() {
        Jwt jwt = currentJwt();
        if (jwt == null) return;  // no JWT → fall through, perm check handles deny
        String userId = jwt.getSubject();
        if (userId == null) return;
        long kickAt = forceLogoutService.kickOutAt(userId);
        if (kickAt <= 0) return;
        Instant iat = jwt.getIssuedAt();
        if (iat == null) return;
        // <= rather than < — JWT iat is second-precision; a kick that lands within
        // the same second as token issue should still terminate the session.
        if (iat.getEpochSecond() <= kickAt) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "Session terminated by administrator");
        }
    }

    private Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return jat.getToken();
        }
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            return jwtDecoder.decode(header.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) return sra.getRequest();
        return null;
    }
}
