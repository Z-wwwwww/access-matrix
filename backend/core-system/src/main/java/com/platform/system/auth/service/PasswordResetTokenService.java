package com.platform.system.auth.service;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.auth.entity.PasswordResetTokenEntity;
import com.platform.system.auth.mapper.PasswordResetTokenMapper;
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
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Mint / peek / consume single-use password-reset tokens. Reverse
 * counterpart of {@link InviteTokenService}: used by the
 * SSO → password migration to let KC-bound users self-serve their own
 * password into {@code core_auth_user.password_hash}.
 *
 * <p>Token design (identical to InviteTokenService):
 * <ul>
 *   <li>32 random bytes → base64url-encoded → email URL.</li>
 *   <li>DB stores ONLY the SHA-256 hex — DB dump can't be used to log in.</li>
 *   <li>Single-use: {@link #consume} atomically sets used_at; replays fail
 *       at the "not active" check.</li>
 * </ul>
 *
 * <p>Why a second service instead of generalizing InviteTokenService:
 * the two tokens have different semantics (invite → set first password
 * via KC; reset → set new password via our own bcrypt). Keeping them
 * separate avoids a leaky abstraction and lets each service evolve its
 * row schema independently (e.g. reset tokens may later need a "reason"
 * column).
 */
@Service
public class PasswordResetTokenService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);

    private final PasswordResetTokenMapper mapper;
    private final SecureRandom rng = new SecureRandom();

    /**
     * Token lifetime — overridable via {@code app.password-reset.token-ttl},
     * default 7 days. Falls back to the invite ttl behavior when unset.
     */
    @Value("${app.password-reset.token-ttl:7d}")
    private Duration ttl;

    public PasswordResetTokenService(PasswordResetTokenMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Effective token lifetime in whole days — what reset emails must quote as
     * the validity period, so the number stays in sync with the configured
     * {@code app.password-reset.token-ttl} instead of a hardcoded literal.
     * Mirror of {@link InviteTokenService#ttlDays()} (same fallback to 7, same
     * floor of 1 so a sub-day TTL never renders as "0 days"). Added because the
     * reset email was quoting a literal "7" while this TTL is configurable —
     * exactly the drift the invite side calls out in its own comment.
     */
    public long ttlDays() {
        Duration d = ttl != null ? ttl : Duration.ofDays(7);
        return Math.max(1, d.toDays());
    }

    @Transactional
    public String mint(String tenantId, String userId, String keycloakId) {
        if (ttl == null) ttl = Duration.ofDays(7);
        String cleartext = randomToken();
        PasswordResetTokenEntity row = new PasswordResetTokenEntity();
        row.setId(IdGenerator.ulid());
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setKeycloakId(keycloakId);
        row.setTokenHash(sha256Hex(cleartext));
        row.setExpiresAt(OffsetDateTime.now().plus(ttl));
        mapper.insert(row);
        log.info("[reset] minted token for user {} (tenant {}) expires {}", userId, tenantId, row.getExpiresAt());
        return cleartext;
    }

    /**
     * Look up an outstanding reset token without consuming. Returns null on
     * any kind of failure so the caller can render a generic "no longer valid"
     * screen without leaking which case it is.
     */
    public PasswordResetTokenEntity peek(String cleartextToken) {
        PasswordResetTokenEntity row = mapper.findActiveByTokenHash(sha256Hex(cleartextToken));
        if (row == null) return null;
        if (row.getExpiresAt() == null || row.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return null;
        }
        return row;
    }

    /**
     * Consume the token. Throws {@link BusinessException} on
     * not-found / expired / already-used. On success returns the row so the
     * caller can pull {@code user_id} / {@code keycloak_id} for the
     * post-reset bookkeeping.
     */
    @Transactional
    public PasswordResetTokenEntity consume(String cleartextToken) {
        PasswordResetTokenEntity row = mapper.findActiveByTokenHash(sha256Hex(cleartextToken));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Reset token not found or already used");
        }
        if (row.getExpiresAt() == null || row.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Reset token has expired");
        }
        // Atomic single-use claim: only the FIRST caller flips used_at NULL → now.
        // markUsed returns the affected-row count — 0 means it was already consumed
        // (or lost a concurrent race), so we reject. The previous UpdateWrapper
        // version discarded the count, so two concurrent POSTs of the same reset
        // link both got past here and each wrote a password hash — defeating the
        // single-use guarantee PasswordResetController.accept explicitly relies on.
        // Same fix (and same shape) as InviteTokenService.consume.
        OffsetDateTime now = OffsetDateTime.now();
        if (mapper.markUsed(row.getId(), now) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Reset token not found or already used");
        }
        row.setUsedAt(now);
        log.info("[reset] consumed token for user {} (tenant {})", row.getUserId(), row.getTenantId());
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
