package com.platform.system.auth.service;

import com.platform.system.auth.dto.TokenResponse;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.service.PermissionQueryService;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.security.PermissionMatcher;
import com.platform.core.infrastructure.security.AccountLockoutService;
import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.JwtIssuer;
import com.platform.core.infrastructure.security.RefreshTokenStore;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // A pre-computed BCrypt hash to use during user-not-found "fake compare" — prevents timing leaks.
    private static final String DUMMY_BCRYPT =
            "$2a$12$abcdefghijklmnopqrstuu7K3jvA1nv/9p2eYJpZxRZ8ZeKbV.r4u";

    static final String COMPACT_MARKER = "__compact__";

    private final UserMapper userMapper;
    private final PasswordEncoder encoder;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenStore refreshStore;
    private final AccountLockoutService lockoutService;
    private final LoginAuditService auditService;
    private final PermissionQueryService permissionQueryService;
    private final RoleMapper roleMapper;
    private final ForceLogoutService forceLogoutService;

    public AuthService(UserMapper userMapper, PasswordEncoder encoder, JwtIssuer jwtIssuer,
                       RefreshTokenStore refreshStore, AccountLockoutService lockoutService,
                       LoginAuditService auditService,
                       PermissionQueryService permissionQueryService,
                       RoleMapper roleMapper,
                       ForceLogoutService forceLogoutService) {
        this.userMapper = userMapper;
        this.encoder = encoder;
        this.jwtIssuer = jwtIssuer;
        this.refreshStore = refreshStore;
        this.lockoutService = lockoutService;
        this.auditService = auditService;
        this.permissionQueryService = permissionQueryService;
        this.roleMapper = roleMapper;
        this.forceLogoutService = forceLogoutService;
    }

    public LoginResult login(String identifier, String password, HttpServletRequest req) {
        String clientIp = clientIp(req);
        String userAgent = req.getHeader("User-Agent");
        // CoreRequestContextFilter has already resolved the tenant for this
        // pre-auth request from X-Tenant-Id (or filled in "default"). We must
        // pass it through the hand-written @Select since the MyBatis-Plus
        // tenant interceptor does not rewrite raw SQL.
        String tenantId = currentTenantOrDefault();

        UserEntity user = userMapper.findByIdentifier(tenantId, identifier);
        if (user == null) {
            encoder.matches(password, DUMMY_BCRYPT); // timing-safe dummy compare
            auditService.record(tenantId, null, identifier, clientIp, userAgent, false, "user-not-found");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Bad credentials");
        }

        long remaining = lockoutService.remainingLockSeconds(tenantId, identifier);
        if (remaining > 0) {
            auditService.record(tenantId, user.getId(), identifier, clientIp, userAgent, false, "account-locked");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Account locked. Try again in " + remaining + " seconds.");
        }

        if (!encoder.matches(password, user.getPasswordHash())) {
            lockoutService.recordFailure(tenantId, identifier);
            auditService.record(tenantId, user.getId(), identifier, clientIp, userAgent, false, "bad-credentials");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Bad credentials");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            auditService.record(tenantId, user.getId(), identifier, clientIp, userAgent, false, "account-disabled");
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        lockoutService.reset(tenantId, identifier);
        TokenResponse tokens = issueTokens(user);
        auditService.record(tenantId, user.getId(), identifier, clientIp, userAgent, true, null);
        return new LoginResult(user, tokens);
    }

    public TokenResponse refresh(String refreshToken) {
        RefreshTokenStore.Holder holder = refreshStore.rotate(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid refresh token"));
        String userId = holder.userId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid refresh token");
        }
        // Force-logout cuts off /auth/refresh too — otherwise a kicked-out user
        // could ride their old refresh token to a fresh access token. A legacy
        // entry without issuedAt is treated as "issued before any conceivable
        // kick" so it always loses to a non-zero kickOutAt.
        long kickAt = forceLogoutService.kickOutAt(userId);
        if (kickAt > 0 && holder.issuedAtEpochSec() <= kickAt) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Session terminated by administrator");
        }
        // Resolve the user under the tenant baked into the refresh token, not
        // whatever X-Tenant-Id the refresh request happens to carry — otherwise
        // a missing/mismatched header would silently invalidate a legitimate
        // (already-rotated) token under the MyBatis-Plus tenant interceptor.
        UserEntity user = userMapper.findByIdAndTenant(userId, holder.tenantId());
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "User not found");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) refreshStore.revoke(refreshToken);
    }

    public TokenResponse issueTokens(UserEntity user) {
        Set<String> perms = permissionQueryService.loadUserPermissions(user.getId());
        List<String> roleIds = roleMapper.findRoleIdsByUserId(user.getId(), user.getTenantId());
        String scopeClaim = chooseScopeClaim(perms);

        JwtIssuer.TokenIssue access = jwtIssuer.issue(
                user.getId(), user.getTenantId(), user.getUsername(), scopeClaim, roleIds);
        String refresh = refreshStore.issue(user.getId(), user.getTenantId());
        return TokenResponse.of(access.token(), refresh, access.expiresInSec());
    }

    /**
     * Encode the user's permissions into the JWT scope claim.
     *
     * <p>We deliberately never inline the actual permission codes:
     * <ul>
     *   <li>Super admin → {@code "*:*"} (zero permission lookup needed)</li>
     *   <li>Empty set  → {@code ""} (caller has no authorities)</li>
     *   <li>Anything else → {@link #COMPACT_MARKER}; resolver fetches via
     *       {@code UserPermissionsLookup} (Caffeine-cached)</li>
     * </ul>
     *
     * <p>Reason for going compact-only: inlining freezes the permission set
     * at token-issue time, so an admin's role/permission revoke can't take
     * effect until the JWT expires. The cached lookup path lets
     * {@code PermissionCacheService.evictRole} invalidate the next request.
     */
    static String chooseScopeClaim(Set<String> perms) {
        if (perms == null || perms.isEmpty()) return "";
        if (perms.contains(PermissionMatcher.SUPER)) return PermissionMatcher.SUPER;
        return COMPACT_MARKER;
    }

    public static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : Arrays.asList(trimmed.split(","))) {
            String s = part.trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static String currentTenantOrDefault() {
        String tid = RequestContext.tenantId();
        return (tid == null || tid.isBlank()) ? "default" : tid;
    }

    public static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xrip = req.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) return xrip.trim();
        return req.getRemoteAddr();
    }

    public record LoginResult(UserEntity user, TokenResponse tokens) {}
}
