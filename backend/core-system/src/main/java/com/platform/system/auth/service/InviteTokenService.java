package com.platform.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.auth.entity.UserInviteEntity;
import com.platform.system.auth.mapper.UserInviteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Mint / validate / consume single-use invite tokens. See V22 migration
 * for the storage shape and {@link UserInviteEntity}.
 *
 * <p>Token design:
 * <ul>
 *   <li>32 random bytes (256 bits) → base64url-encoded (43 chars) →
 *       lives only in the recipient's email URL.</li>
 *   <li>The DB stores only the SHA-256 hex of that string. Even a full
 *       DB dump can't be used to log in.</li>
 *   <li>Single-use: {@link #consume} sets {@code used_at} atomically
 *       with the password change; replays go straight to "already used".</li>
 * </ul>
 */
@Service
public class InviteTokenService {

    private static final Logger log = LoggerFactory.getLogger(InviteTokenService.class);

    private final UserInviteMapper inviteMapper;
    private final SecureRandom rng = new SecureRandom();

    /** Token lifetime — overridable via {@code app.invite.token-ttl}, default 7 days. */
    @Value("${app.invite.token-ttl:7d}")
    private Duration ttl;

    public InviteTokenService(UserInviteMapper inviteMapper) {
        this.inviteMapper = inviteMapper;
    }

    /**
     * Generate a new invite token, persist its hash, and return the
     * cleartext token (caller will embed it in the email URL).
     *
     * @return the cleartext token — DO NOT log this. It's the email's secret.
     */
    @Transactional
    public String mint(String tenantId, String userId, String keycloakId) {
        if (ttl == null) ttl = Duration.ofDays(7);
        String cleartext = randomToken();
        UserInviteEntity row = new UserInviteEntity();
        row.setId(IdGenerator.ulid());
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setKeycloakId(keycloakId);
        row.setTokenHash(sha256Hex(cleartext));
        row.setExpiresAt(LocalDateTime.now().plus(ttl));
        inviteMapper.insert(row);
        log.info("[invite] minted token for user {} (tenant {}) expires {}", userId, tenantId, row.getExpiresAt());
        return cleartext;
    }

    /**
     * Look up an outstanding invite by its cleartext token without consuming
     * it — used by the frontend acceptance page to render "set your password"
     * BEFORE the user submits. Returns null on any kind of failure
     * (not-found / expired / used) — the page should redirect to a generic
     * "this invite is no longer valid" screen rather than leak which case it is.
     */
    public UserInviteEntity peek(String cleartextToken) {
        UserInviteEntity row = inviteMapper.findActiveByTokenHash(sha256Hex(cleartextToken));
        if (row == null) return null;
        if (row.getExpiresAt() == null || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return row;
    }

    /**
     * Consume an invite token. Throws {@link BusinessException} on
     * not-found / expired / already-used so the caller can surface a clear
     * error to the user. On success, marks the row used_at = now and returns
     * the row so the caller can pull keycloak_id / user_id from it.
     */
    @Transactional
    public UserInviteEntity consume(String cleartextToken) {
        UserInviteEntity row = inviteMapper.findActiveByTokenHash(sha256Hex(cleartextToken));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Invite token not found or already used");
        }
        if (row.getExpiresAt() == null || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Invite token has expired");
        }
        // Atomic single-use: set used_at via UpdateWrapper (mark is @TableLogic
        // so updateById would strip it; we're not touching mark here, but the
        // same SET-clause discipline as the rest of the codebase applies).
        LocalDateTime now = LocalDateTime.now();
        inviteMapper.update(null,
                new UpdateWrapper<UserInviteEntity>()
                        .eq("id", row.getId())
                        .isNull("used_at")
                        .set("used_at", now)
                        .set("update_user", "system"));
        row.setUsedAt(now);
        log.info("[invite] consumed token for user {} (tenant {})", row.getUserId(), row.getTenantId());
        return row;
    }

    private String randomToken() {
        byte[] b = new byte[32];
        rng.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
