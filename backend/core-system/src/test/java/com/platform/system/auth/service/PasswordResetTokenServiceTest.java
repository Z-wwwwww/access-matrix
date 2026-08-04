package com.platform.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.error.BusinessException;
import com.platform.system.auth.entity.PasswordResetTokenEntity;
import com.platform.system.auth.mapper.PasswordResetTokenMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirror of {@link InviteTokenServiceTest} for the reverse (SSO → password)
 * migration token. Same invariants, because the two tokens guard the same kind
 * of thing — a pre-auth link that sets a credential:
 *
 *   1. mint() persists ONLY the SHA-256 hash, returns the cleartext.
 *   2. peek() returns null for expired tokens and never consumes.
 *   3. consume() raises BusinessException on expired / not-found.
 *   4. consume() claims single-use atomically: the markUsed UPDATE's
 *      affected-row count IS the claim — 0 rows means another request already
 *      flipped used_at, and consume must reject. PasswordResetController.accept
 *      calls consume() FIRST precisely so a retried / double-submitted POST
 *      can't double-spend the token across the bcrypt + KC.disableUser legs;
 *      that guarantee only holds if the row count is checked here.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock PasswordResetTokenMapper mapper;
    @InjectMocks PasswordResetTokenService service;

    @BeforeEach
    void setTtl() {
        // @Value defaults don't apply without a Spring context.
        ReflectionTestUtils.setField(service, "ttl", Duration.ofDays(7));
    }

    private static String sha256(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(h.length * 2);
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static PasswordResetTokenEntity storedRow(String cleartext, OffsetDateTime expiresAt)
            throws NoSuchAlgorithmException {
        PasswordResetTokenEntity row = new PasswordResetTokenEntity();
        row.setId("ulid-row");
        row.setUserId("ulid-user");
        row.setKeycloakId("kc-uuid");
        row.setTenantId("acme");
        row.setTokenHash(sha256(cleartext));
        row.setExpiresAt(expiresAt);
        return row;
    }

    @Test
    void mint_persistsHashOnly_returnsCleartext() {
        String cleartext = service.mint("acme", "ulid-user", "kc-uuid");

        assertThat(cleartext).hasSizeGreaterThan(40);
        assertThat(cleartext).matches("[A-Za-z0-9_-]+");

        ArgumentCaptor<PasswordResetTokenEntity> cap =
                ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(mapper).insert(cap.capture());
        PasswordResetTokenEntity row = cap.getValue();

        // A DB dump alone must not be usable to set someone's password.
        assertThat(row.getTokenHash()).isNotEqualTo(cleartext);
        assertThat(row.getTokenHash()).hasSize(64);   // SHA-256 hex
        assertThat(row.getUserId()).isEqualTo("ulid-user");
        assertThat(row.getKeycloakId()).isEqualTo("kc-uuid");
        assertThat(row.getTenantId()).isEqualTo("acme");
        assertThat(row.getExpiresAt()).isAfter(OffsetDateTime.now().plusDays(6));
        assertThat(row.getExpiresAt()).isBefore(OffsetDateTime.now().plusDays(8));
        assertThat(row.getUsedAt()).isNull();
    }

    @Test
    void mint_twoCallsProduceDifferentCleartextTokens() {
        assertThat(service.mint("acme", "u1", "kc1"))
                .isNotEqualTo(service.mint("acme", "u2", "kc2"));
    }

    @Test
    void peek_returnsNullWhenExpired() throws Exception {
        String cleartext = "the-secret";
        when(mapper.findActiveByTokenHash(sha256(cleartext)))
                .thenReturn(storedRow(cleartext, OffsetDateTime.now().minusMinutes(1)));

        assertThat(service.peek(cleartext)).isNull();
        // peek() is a probe — it must never claim the token.
        verify(mapper, never()).markUsed(any(), any());
        verify(mapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void peek_returnsRowWhenValid() throws Exception {
        String cleartext = "valid-secret";
        PasswordResetTokenEntity stored = storedRow(cleartext, OffsetDateTime.now().plusDays(1));
        when(mapper.findActiveByTokenHash(sha256(cleartext))).thenReturn(stored);

        assertThat(service.peek(cleartext)).isSameAs(stored);
    }

    @Test
    void consume_marksUsedAtomically() throws Exception {
        String cleartext = "to-consume";
        when(mapper.findActiveByTokenHash(sha256(cleartext)))
                .thenReturn(storedRow(cleartext, OffsetDateTime.now().plusDays(1)));
        when(mapper.markUsed(eq("ulid-row"), any(OffsetDateTime.class))).thenReturn(1);

        PasswordResetTokenEntity consumed = service.consume(cleartext);

        assertThat(consumed.getUsedAt()).isNotNull();
        verify(mapper).markUsed(eq("ulid-row"), any(OffsetDateTime.class));
    }

    @Test
    void consume_rejectsWhenAlreadyClaimed() throws Exception {
        // The regression this test exists for: markUsed affecting 0 rows means a
        // concurrent request already consumed the link. Without checking the count
        // both callers proceeded and each wrote a password hash.
        String cleartext = "raced";
        when(mapper.findActiveByTokenHash(sha256(cleartext)))
                .thenReturn(storedRow(cleartext, OffsetDateTime.now().plusDays(1)));
        when(mapper.markUsed(eq("ulid-row"), any(OffsetDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> service.consume(cleartext))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void consume_throwsBusinessExceptionOnExpired() throws Exception {
        String cleartext = "expired";
        when(mapper.findActiveByTokenHash(sha256(cleartext)))
                .thenReturn(storedRow(cleartext, OffsetDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() -> service.consume(cleartext))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");

        verify(mapper, never()).markUsed(any(), any());
    }

    @Test
    void consume_throwsBusinessExceptionOnNotFound() {
        when(mapper.findActiveByTokenHash(any())).thenReturn(null);

        assertThatThrownBy(() -> service.consume("any-cleartext"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");

        verify(mapper, never()).markUsed(any(), any());
    }
}
