package com.platform.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.error.BusinessException;
import com.platform.system.auth.entity.UserInviteEntity;
import com.platform.system.auth.mapper.UserInviteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
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
 * Pins the invite-token lifecycle invariants:
 *
 *   1. mint() persists ONLY the SHA-256 hash, returns the cleartext token
 *      (the cleartext never lives in DB — it's the email's secret).
 *   2. peek() returns null for expired tokens (vs raising — UI uses null
 *      to render a generic "invite no longer valid" without leaking why).
 *   3. consume() raises BusinessException on expired / not-found / used.
 *   4. consume() claims single-use atomically: the markUsed UPDATE's
 *      affected-row count is the claim — 0 rows means already consumed
 *      (or a lost race) and must reject, never silently allow reuse.
 */
@ExtendWith(MockitoExtension.class)
class InviteTokenServiceTest {

    @Mock UserInviteMapper inviteMapper;
    @InjectMocks InviteTokenService service;

    @BeforeEach
    void setTtl() {
        // @Value defaults don't apply without Spring context.
        ReflectionTestUtils.setField(service, "ttl", Duration.ofDays(7));
    }

    private static String sha256(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(h.length * 2);
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    void mint_persistsHashOnly_returnsCleartext() {
        String cleartext = service.mint("acme", "ulid-user", "kc-uuid");

        // Cleartext is the email-side secret: long, opaque, base64url-shaped.
        assertThat(cleartext).hasSizeGreaterThan(40);
        assertThat(cleartext).matches("[A-Za-z0-9_-]+");

        ArgumentCaptor<UserInviteEntity> cap = ArgumentCaptor.forClass(UserInviteEntity.class);
        verify(inviteMapper).insert(cap.capture());
        UserInviteEntity row = cap.getValue();

        // Critical: persisted hash != cleartext. A DB dump alone can't be used to log in.
        assertThat(row.getTokenHash()).isNotEqualTo(cleartext);
        assertThat(row.getTokenHash()).hasSize(64); // SHA-256 hex = 64 chars
        assertThat(row.getUserId()).isEqualTo("ulid-user");
        assertThat(row.getKeycloakId()).isEqualTo("kc-uuid");
        assertThat(row.getTenantId()).isEqualTo("acme");
        assertThat(row.getExpiresAt()).isAfter(OffsetDateTime.now().plusDays(6));
        assertThat(row.getExpiresAt()).isBefore(OffsetDateTime.now().plusDays(8));
        assertThat(row.getUsedAt()).isNull();
    }

    @Test
    void peek_returnsNullWhenExpired() throws Exception {
        String cleartext = "the-secret";
        UserInviteEntity stored = new UserInviteEntity();
        stored.setId("ulid-row");
        stored.setTokenHash(sha256(cleartext));
        stored.setExpiresAt(OffsetDateTime.now().minusMinutes(1)); // expired
        when(inviteMapper.findActiveByTokenHash(sha256(cleartext))).thenReturn(stored);

        assertThat(service.peek(cleartext)).isNull();
        // Critical: peek() must NOT consume — it's just a probe.
        verify(inviteMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void peek_returnsRowWhenValid() throws Exception {
        String cleartext = "valid-secret";
        UserInviteEntity stored = new UserInviteEntity();
        stored.setId("ulid-row");
        stored.setTokenHash(sha256(cleartext));
        stored.setExpiresAt(OffsetDateTime.now().plusDays(1));
        when(inviteMapper.findActiveByTokenHash(sha256(cleartext))).thenReturn(stored);

        assertThat(service.peek(cleartext)).isSameAs(stored);
    }

    @Test
    void consume_marksUsedAtomically() throws Exception {
        String cleartext = "to-consume";
        UserInviteEntity stored = new UserInviteEntity();
        stored.setId("ulid-row");
        stored.setUserId("ulid-user");
        stored.setKeycloakId("kc-uuid");
        stored.setTenantId("acme");
        stored.setTokenHash(sha256(cleartext));
        stored.setExpiresAt(OffsetDateTime.now().plusDays(1));
        when(inviteMapper.findActiveByTokenHash(sha256(cleartext))).thenReturn(stored);
        when(inviteMapper.markUsed(eq("ulid-row"), any(OffsetDateTime.class))).thenReturn(1);

        UserInviteEntity consumed = service.consume(cleartext);

        assertThat(consumed.getUsedAt()).isNotNull();
        verify(inviteMapper).markUsed(eq("ulid-row"), any(OffsetDateTime.class));
    }

    @Test
    void consume_rejectsWhenAlreadyClaimed() throws Exception {
        // The single-use claim is the markUsed UPDATE's affected-row count: 0
        // means another request already flipped used_at (race / re-click) —
        // consume must reject instead of silently letting the token be reused.
        String cleartext = "raced";
        UserInviteEntity stored = new UserInviteEntity();
        stored.setId("ulid-row");
        stored.setTokenHash(sha256(cleartext));
        stored.setExpiresAt(OffsetDateTime.now().plusDays(1));
        when(inviteMapper.findActiveByTokenHash(sha256(cleartext))).thenReturn(stored);
        when(inviteMapper.markUsed(eq("ulid-row"), any(OffsetDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> service.consume(cleartext))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.invite.notFoundOrUsed");
    }

    @Test
    void consume_throwsBusinessExceptionOnExpired() throws Exception {
        String cleartext = "expired";
        UserInviteEntity stored = new UserInviteEntity();
        stored.setTokenHash(sha256(cleartext));
        stored.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(inviteMapper.findActiveByTokenHash(sha256(cleartext))).thenReturn(stored);

        assertThatThrownBy(() -> service.consume(cleartext))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");

        verify(inviteMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void consume_throwsBusinessExceptionOnNotFound() {
        when(inviteMapper.findActiveByTokenHash(any())).thenReturn(null);

        assertThatThrownBy(() -> service.consume("any-cleartext"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.invite.notFoundOrUsed");

        verify(inviteMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void mint_twoCallsProduceDifferentCleartextTokens() {
        String a = service.mint("acme", "u1", "kc1");
        String b = service.mint("acme", "u2", "kc2");
        assertThat(a).isNotEqualTo(b);
    }
}
