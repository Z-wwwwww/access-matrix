package com.platform.system.auth.service;

import com.platform.system.auth.dto.TokenResponse;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.infrastructure.security.AccountLockoutService;
import com.platform.core.infrastructure.security.JwtIssuer;
import com.platform.core.infrastructure.security.RefreshTokenStore;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // A pre-computed BCrypt hash to use during user-not-found "fake compare" — prevents timing leaks.
    private static final String DUMMY_BCRYPT =
            "$2a$12$abcdefghijklmnopqrstuu7K3jvA1nv/9p2eYJpZxRZ8ZeKbV.r4u";

    private final UserMapper userMapper;
    private final PasswordEncoder encoder;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenStore refreshStore;
    private final AccountLockoutService lockoutService;
    private final LoginAuditService auditService;

    public AuthService(UserMapper userMapper, PasswordEncoder encoder, JwtIssuer jwtIssuer,
                       RefreshTokenStore refreshStore, AccountLockoutService lockoutService,
                       LoginAuditService auditService) {
        this.userMapper = userMapper;
        this.encoder = encoder;
        this.jwtIssuer = jwtIssuer;
        this.refreshStore = refreshStore;
        this.lockoutService = lockoutService;
        this.auditService = auditService;
    }

    public LoginResult login(String identifier, String password, HttpServletRequest req) {
        String clientIp = clientIp(req);
        String userAgent = req.getHeader("User-Agent");

        UserEntity user = userMapper.findByIdentifier(identifier);
        if (user == null) {
            encoder.matches(password, DUMMY_BCRYPT); // timing-safe dummy compare
            auditService.record(null, identifier, clientIp, userAgent, false, "user-not-found");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Bad credentials");
        }

        long remaining = lockoutService.remainingLockSeconds(identifier);
        if (remaining > 0) {
            auditService.record(user.getId(), identifier, clientIp, userAgent, false, "account-locked");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Account locked. Try again in " + remaining + " seconds.");
        }

        if (!encoder.matches(password, user.getPasswordHash())) {
            lockoutService.recordFailure(identifier);
            auditService.record(user.getId(), identifier, clientIp, userAgent, false, "bad-credentials");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Bad credentials");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            auditService.record(user.getId(), identifier, clientIp, userAgent, false, "account-disabled");
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        lockoutService.reset(identifier);
        TokenResponse tokens = issueTokens(user);
        auditService.record(user.getId(), identifier, clientIp, userAgent, true, null);
        return new LoginResult(user, tokens);
    }

    public TokenResponse refresh(String refreshToken) {
        String userId = refreshStore.rotate(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid refresh token"));
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getMark() == null || user.getMark() != 1) {
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
        Collection<String> auths = parseJsonArray(user.getAuthorities());
        JwtIssuer.TokenIssue access = jwtIssuer.issue(
                user.getId(), user.getTenantId(), user.getUsername(), auths);
        String refresh = refreshStore.issue(user.getId());
        return TokenResponse.of(access.token(), refresh, access.expiresInSec());
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
