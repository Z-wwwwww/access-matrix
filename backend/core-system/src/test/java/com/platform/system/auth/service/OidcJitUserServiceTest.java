package com.platform.system.auth.service;

import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the three JIT provisioning branches that {@link OidcJitUserService}
 * has to keep straight:
 *
 *   1. fast path — token already bound (matches keycloak_id);
 *   2. bind path — legacy password user discovered by (tenant, username),
 *      we write keycloak_id so future requests take path 1;
 *   3. provision path — neither matches, insert a brand new row with a
 *      fresh ULID and the basic profile fields seeded from token claims.
 *
 * Also pins the fail-closed behaviour when the token is missing the
 * essential claims (sub or tid).
 */
@ExtendWith(MockitoExtension.class)
class OidcJitUserServiceTest {

    @Mock UserMapper userMapper;
    @InjectMocks OidcJitUserService service;

    @BeforeEach
    void wireClaimNames() {
        // @Value defaults aren't applied without a Spring context — set them
        // by hand to match application.yml's default mapper config.
        ReflectionTestUtils.setField(service, "tenantClaim", "tid");
        ReflectionTestUtils.setField(service, "usernameClaim", "preferred_username");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "header.payload.signature",
                Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                claims);
    }

    private UserEntity row(String id, String username) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setStatus(1);
        u.setMark(1);
        return u;
    }

    @Test
    void fastPath_alreadyBound_returnsExistingBusinessId() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-1",
                "tid", "default",
                "preferred_username", "alice"));
        UserEntity bound = row("ULID-ALICE-26", "alice");
        bound.setKeycloakId("kc-uuid-1");
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-1", "default")).thenReturn(bound);

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isEqualTo("ULID-ALICE-26");
        // Pure read path — no insert, no bind-update.
        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void bindPath_legacyUsernameMatch_writesKeycloakIdAndReturnsLegacyId() {
        // First SSO login for a user that pre-existed in the password flow.
        // The bind must be done with updateById (not @TableLogic — we're not
        // changing mark, just keycloak_id), otherwise the link doesn't land.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-2",
                "tid", "default",
                "preferred_username", "bob"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-2", "default")).thenReturn(null);
        UserEntity legacy = row("ULID-BOB-26", "bob");
        when(userMapper.findByIdentifier("default", "bob")).thenReturn(legacy);

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isEqualTo("ULID-BOB-26");
        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(cap.capture());
        assertThat(cap.getValue().getKeycloakId()).isEqualTo("kc-uuid-2");
        assertThat(cap.getValue().getId()).isEqualTo("ULID-BOB-26");
        // No JIT insert when we successfully bound.
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    void provisionPath_brandNewUser_insertsWithUlidAndSeedsProfile() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-3",
                "tid", "acme",
                "preferred_username", "carol",
                "email", "carol@acme.example",
                "name", "Carol Carolsdottir"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-3", "acme")).thenReturn(null);
        when(userMapper.findByIdentifier("acme", "carol")).thenReturn(null);

        String businessId = service.resolveBusinessUserId(token);

        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(cap.capture());
        UserEntity inserted = cap.getValue();
        assertThat(inserted.getId()).hasSize(26);      // ULID
        assertThat(businessId).isEqualTo(inserted.getId());
        assertThat(inserted.getKeycloakId()).isEqualTo("kc-uuid-3");
        assertThat(inserted.getTenantId()).isEqualTo("acme");
        assertThat(inserted.getUsername()).isEqualTo("carol");
        assertThat(inserted.getEmail()).isEqualTo("carol@acme.example");
        assertThat(inserted.getDisplayName()).isEqualTo("Carol Carolsdottir");
        assertThat(inserted.getStatus()).isEqualTo(1);
        // userNo intentionally NOT auto-numbered — admin must do that
        // explicitly so the audit trail attributes the number to a person.
        assertThat(inserted.getUserNo()).isNull();
        // No password — these users authenticate via the IdP.
        assertThat(inserted.getPasswordHash()).isNull();
    }

    @Test
    void provisionPath_noNameClaim_fallsBackToGivenFamily() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-4",
                "tid", "default",
                "preferred_username", "dave",
                "given_name", "Dave",
                "family_name", "Smith"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-4", "default")).thenReturn(null);
        when(userMapper.findByIdentifier("default", "dave")).thenReturn(null);

        service.resolveBusinessUserId(token);

        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getDisplayName()).isEqualTo("Dave Smith");
    }

    @Test
    void provisionPath_noNameOrGivenFamily_fallsBackToUsername() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-5",
                "tid", "default",
                "preferred_username", "eve"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-5", "default")).thenReturn(null);
        when(userMapper.findByIdentifier("default", "eve")).thenReturn(null);

        service.resolveBusinessUserId(token);

        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getDisplayName()).isEqualTo("eve");
    }

    @Test
    void missingTid_returnsNull_andDoesNotTouchDatabase() {
        // Fail-closed: a token without tenant claim shouldn't silently
        // provision into "default" — that would silently fold cross-tenant
        // tokens into one tenant's data.
        Map<String, Object> noTid = new HashMap<>();
        noTid.put("sub", "kc-uuid-6");
        noTid.put("preferred_username", "frank");
        Jwt token = jwt(noTid);

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isNull();
        verify(userMapper, never()).findByKeycloakIdAndTenant(any(), any());
        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void missingSub_returnsNull() {
        Map<String, Object> noSub = new HashMap<>();
        noSub.put("tid", "default");
        noSub.put("preferred_username", "grace");
        // Jwt constructor requires "sub" - we have to give it something. Use empty + reflect.
        Jwt token = new Jwt(
                "header.payload.signature",
                Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("sub", "", "tid", "default", "preferred_username", "grace"));

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isNull();
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

}
