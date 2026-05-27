package com.platform.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.mapper.RoleMapper;
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
    @Mock RoleMapper roleMapper;
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
                "tid", "demo",
                "preferred_username", "alice"));
        UserEntity bound = row("ULID-ALICE-26", "alice");
        bound.setKeycloakId("kc-uuid-1");
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-1", "demo")).thenReturn(bound);

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isEqualTo("ULID-ALICE-26");
        // Pure read path — no insert, no bind-update.
        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void bindPath_legacyNonSuperAdminUser_writesKeycloakIdAndClearsPasswordHash() {
        // First SSO login for a user that pre-existed in the password flow.
        // The bind UPDATE writes keycloak_id AND nulls out password_hash so
        // the row ends up byte-identical to a fresh OIDC JIT user — that's
        // the "as-if-always-OIDC" end state the runbook promises.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-2",
                "tid", "demo",
                "preferred_username", "bob"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-2", "demo")).thenReturn(null);
        UserEntity legacy = row("ULID-BOB-26", "bob");
        when(userMapper.findByIdentifier("demo", "bob")).thenReturn(legacy);
        // Bob is NOT a super-admin — role lookup returns an empty list.
        when(roleMapper.findRoleIdsByUserId("ULID-BOB-26", "demo")).thenReturn(java.util.List.of());

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isEqualTo("ULID-BOB-26");
        ArgumentCaptor<UpdateWrapper<UserEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(org.mockito.ArgumentMatchers.isNull(), cap.capture());
        // MP UpdateWrapper parameterises values into paramNameValuePairs;
        // getSqlSet() returns the placeholder template. Read both to
        // verify intent.
        String sql = cap.getValue().getSqlSet();
        java.util.Map<String, Object> params = cap.getValue().getParamNameValuePairs();
        assertThat(sql).contains("keycloak_id=");
        assertThat(sql).contains("password_hash=");           // password_hash IS in the SET clause
        assertThat(params.values()).contains("kc-uuid-2");
        assertThat(params.values()).contains((Object) null);  // and its value is NULL
        // No JIT insert when we successfully bound.
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    void bindPath_legacySuperAdminUser_preservesPasswordHashForBreakGlass() {
        // Same bind path as the non-super-admin case, but the role lookup
        // returns SUPER_ADMIN_ID so the UPDATE must skip password_hash —
        // that's how break-glass access survives the migration.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-2b",
                "tid", "demo",
                "preferred_username", "admin"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-2b", "demo")).thenReturn(null);
        UserEntity legacy = row("ULID-ADMIN-26", "admin");
        when(userMapper.findByIdentifier("demo", "admin")).thenReturn(legacy);
        when(roleMapper.findRoleIdsByUserId("ULID-ADMIN-26", "demo"))
                .thenReturn(java.util.List.of(BuiltInRoles.SUPER_ADMIN_ID));

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isEqualTo("ULID-ADMIN-26");
        ArgumentCaptor<UpdateWrapper<UserEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(org.mockito.ArgumentMatchers.isNull(), cap.capture());
        String sql = cap.getValue().getSqlSet();
        java.util.Map<String, Object> params = cap.getValue().getParamNameValuePairs();
        assertThat(sql).contains("keycloak_id=");
        // The break-glass exemption: password_hash must NOT be in the SET clause at all.
        assertThat(sql).doesNotContain("password_hash");
        assertThat(params.values()).contains("kc-uuid-2b");
        assertThat(params.values()).doesNotContain((Object) null);
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    void bindPath_roleLookupFails_preservesPasswordHashDefensively() {
        // If the role-id lookup itself throws, the safety-first default is
        // to treat the user as super-admin (preserve their hash). Better
        // to leak a stale hash than to silently lock an actual admin out
        // by clearing it during a transient DB hiccup.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-2c",
                "tid", "demo",
                "preferred_username", "carol"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-2c", "demo")).thenReturn(null);
        UserEntity legacy = row("ULID-CAROL-26", "carol");
        when(userMapper.findByIdentifier("demo", "carol")).thenReturn(legacy);
        when(roleMapper.findRoleIdsByUserId("ULID-CAROL-26", "demo"))
                .thenThrow(new RuntimeException("transient DB blip"));

        service.resolveBusinessUserId(token);

        ArgumentCaptor<UpdateWrapper<UserEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(org.mockito.ArgumentMatchers.isNull(), cap.capture());
        String sql = cap.getValue().getSqlSet();
        java.util.Map<String, Object> params = cap.getValue().getParamNameValuePairs();
        assertThat(sql).contains("keycloak_id=");
        // Defensive: password_hash absent from SET clause (treat as super-admin
        // on role-lookup failure).
        assertThat(sql).doesNotContain("password_hash");
        assertThat(params.values()).contains("kc-uuid-2c");
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
                "tid", "demo",
                "preferred_username", "dave",
                "given_name", "Dave",
                "family_name", "Smith"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-4", "demo")).thenReturn(null);
        when(userMapper.findByIdentifier("demo", "dave")).thenReturn(null);

        service.resolveBusinessUserId(token);

        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getDisplayName()).isEqualTo("Dave Smith");
    }

    @Test
    void provisionPath_noNameOrGivenFamily_fallsBackToUsername() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-5",
                "tid", "demo",
                "preferred_username", "eve"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-5", "demo")).thenReturn(null);
        when(userMapper.findByIdentifier("demo", "eve")).thenReturn(null);

        service.resolveBusinessUserId(token);

        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getDisplayName()).isEqualTo("eve");
    }

    @Test
    void missingTid_returnsNull_andDoesNotTouchDatabase() {
        // Fail-closed: a token without tenant claim shouldn't silently
        // provision into "demo" — that would silently fold cross-tenant
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
        noSub.put("tid", "demo");
        noSub.put("preferred_username", "grace");
        // Jwt constructor requires "sub" - we have to give it something. Use empty + reflect.
        Jwt token = new Jwt(
                "header.payload.signature",
                Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("sub", "", "tid", "demo", "preferred_username", "grace"));

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isNull();
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

}
