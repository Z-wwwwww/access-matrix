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
    @Mock com.platform.system.rbac.service.BuiltInRoleLookup roleLookup;
    @Mock com.platform.core.infrastructure.numbering.NumberingService numberingService;
    @Mock com.platform.system.auth.mapper.PasswordResetTokenMapper resetTokenMapper;
    @InjectMocks OidcJitUserService service;

    @BeforeEach
    void wireClaimNames() {
        // @Value defaults aren't applied without a Spring context — set them
        // by hand to match application.yml's default mapper config.
        ReflectionTestUtils.setField(service, "tenantClaim", "tid");
        ReflectionTestUtils.setField(service, "usernameClaim", "preferred_username");
        // The bind path's super-admin check now goes through the lookup.
        // Tests that drive the demo realm need this stubbed to mirror the
        // pre-refactor behaviour (demo's SUPER_ADMIN_ID is the known answer).
        org.mockito.Mockito.lenient()
                .when(roleLookup.superAdminRoleId("demo"))
                .thenReturn(BuiltInRoles.SUPER_ADMIN_ID);
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
        when(userMapper.findByUsernameAndTenant("demo", "bob")).thenReturn(legacy);
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
                "preferred_username", "demo-admin"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-2b", "demo")).thenReturn(null);
        UserEntity legacy = row("ULID-ADMIN-26", "demo-admin");
        when(userMapper.findByUsernameAndTenant("demo", "demo-admin")).thenReturn(legacy);
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
        when(userMapper.findByUsernameAndTenant("demo", "carol")).thenReturn(legacy);
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
        when(userMapper.findByUsernameAndTenant("acme", "carol")).thenReturn(null);
        when(numberingService.next("USER", "acme")).thenReturn("U00000007");

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
        // JIT users are COMPLETE records: a per-tenant user_no is allocated
        // (same numbering as UserAdminService.create) — not left NULL.
        assertThat(inserted.getUserNo()).isEqualTo("U00000007");
        // No password — these users authenticate via the IdP.
        assertThat(inserted.getPasswordHash()).isNull();
    }

    @Test
    void deletedUser_refusesReProvision_returnsNullNoGhostInsert() {
        // Regression: an admin deleted this user (business row now mark=0), but
        // their access token is still valid. The JIT path must NOT create a new
        // roleless "ghost" row — it returns null (→ no business user → SPA logs out).
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-deleted",
                "tid", "demo",
                "preferred_username", "sozo-admin2",
                "given_name", "sozo-admin2",
                "family_name", "sozo-admin2"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-deleted", "demo")).thenReturn(null);
        when(userMapper.countDeletedByKeycloakIdAndTenant("kc-uuid-deleted", "demo")).thenReturn(1L);

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isNull();
        verify(userMapper, never()).insert(any(UserEntity.class));     // no ghost
        verify(userMapper, never()).findByUsernameAndTenant(any(), any());    // refused before legacy-bind
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
        when(userMapper.findByUsernameAndTenant("demo", "dave")).thenReturn(null);

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
        when(userMapper.findByUsernameAndTenant("demo", "eve")).thenReturn(null);

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

    // ── an account that left SSO must not be dragged back onto it ───────────
    //
    // PasswordResetController's reverse migration writes a local password, clears
    // keycloak_id, and disables the Keycloak user — but that last step is
    // BEST-EFFORT ("if KC is unreachable the local password is already written...
    // we just log the orphan KC user for the operator to clean up later"). A
    // surviving (or later re-enabled) KC account can therefore still complete an SSO
    // login, and then: the fast path misses (keycloak_id is NULL), the deleted-user
    // probe misses (mark=1), and the legacy-bind branch matches the SAME user by
    // username and re-writes keycloak_id while NULLING the password_hash they just
    // set. Net effect: a flow the docs call irreversible is silently undone and the
    // user can log in neither way — the reset token is single-use and already spent.
    // core_password_reset_token rows are minted by exactly one caller
    // (SsoToPasswordMigrationService), so a consumed row is an unambiguous marker.

    @Test
    void bindPath_userThatCompletedTheReverseMigration_isRefused() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-orphan",
                "tid", "demo",
                "preferred_username", "dave"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-orphan", "demo")).thenReturn(null);
        UserEntity migrated = row("ULID-DAVE-26", "dave");
        when(userMapper.findByUsernameAndTenant("demo", "dave")).thenReturn(migrated);
        when(resetTokenMapper.countConsumedByUser("demo", "ULID-DAVE-26")).thenReturn(1L);

        assertThat(service.resolveBusinessUserId(token)).isNull();
        // Crucially: no bind UPDATE, so password_hash survives.
        verify(userMapper, never()).update(any(), any());
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    void bindPath_ordinaryLegacyUser_withNoConsumedResetToken_stillBinds() {
        // The guard must not break the normal password->SSO migration it shares a
        // branch with: that user has no consumed reverse-migration token.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-plain",
                "tid", "demo",
                "preferred_username", "erin"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-plain", "demo")).thenReturn(null);
        UserEntity legacy = row("ULID-ERIN-26", "erin");
        when(userMapper.findByUsernameAndTenant("demo", "erin")).thenReturn(legacy);
        when(resetTokenMapper.countConsumedByUser("demo", "ULID-ERIN-26")).thenReturn(0L);
        when(roleMapper.findRoleIdsByUserId("ULID-ERIN-26", "demo")).thenReturn(java.util.List.of());

        assertThat(service.resolveBusinessUserId(token)).isEqualTo("ULID-ERIN-26");
        verify(userMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    // ── disabled user must not resolve on the OIDC path ─────────────────────
    //
    // Disabling is a two-sided write (DB status=0 + Keycloak setEnabled(false)), and
    // only the Keycloak half stops a FRESH sso login. SessionTerminationService
    // applies that half BEST-EFFORT — it logs and swallows a failure so "a KC hiccup
    // must not block the local kick". So with KC briefly unreachable during a
    // disable: status=0 commits, the Redis kick kills the current tokens, then the
    // user signs in again (KC still has them enabled), the fresh token's iat clears
    // ForceLogoutFilter, and this resolver used to return the business id with no
    // status check whatsoever — console says disabled, user keeps working. That is
    // the sozo-admin2 incident, which was fixed only on the Keycloak side.

    @Test
    void fastPath_disabledUser_isRefused() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-disabled",
                "tid", "demo",
                "preferred_username", "bob"));
        UserEntity bound = row("ULID-BOB-26", "bob");
        bound.setKeycloakId("kc-uuid-disabled");
        bound.setStatus(0);
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-disabled", "demo")).thenReturn(bound);

        assertThat(service.resolveBusinessUserId(token)).isNull();
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    void fastPath_enabledUser_stillResolves() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-ok",
                "tid", "demo",
                "preferred_username", "bob"));
        UserEntity bound = row("ULID-BOB-26", "bob");
        bound.setKeycloakId("kc-uuid-ok");
        bound.setStatus(1);
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-ok", "demo")).thenReturn(bound);

        assertThat(service.resolveBusinessUserId(token)).isEqualTo("ULID-BOB-26");
    }

    @Test
    void fastPath_nullStatus_isTreatedAsEnabled() {
        // Legacy rows predating the column's default must not be locked out.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-null",
                "tid", "demo",
                "preferred_username", "bob"));
        UserEntity bound = row("ULID-BOB-26", "bob");
        bound.setKeycloakId("kc-uuid-null");
        bound.setStatus(null);
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-null", "demo")).thenReturn(bound);

        assertThat(service.resolveBusinessUserId(token)).isEqualTo("ULID-BOB-26");
    }

    @Test
    void bindPath_disabledLegacyUser_isRefusedWithoutClearingItsPasswordHash() {
        // Order matters: refuse BEFORE the bind UPDATE, or a rejected login would
        // strip the user's break-glass credential as a side effect.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-legacy-off",
                "tid", "demo",
                "preferred_username", "carol"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-legacy-off", "demo")).thenReturn(null);
        UserEntity legacy = row("ULID-CAROL-26", "carol");
        legacy.setStatus(0);
        when(userMapper.findByUsernameAndTenant("demo", "carol")).thenReturn(legacy);

        assertThat(service.resolveBusinessUserId(token)).isNull();
        verify(userMapper, never()).update(any(), any());
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    // ── claim length vs column width ────────────────────────────────────────
    //
    // These three strings come from the IdP, not from one of our @Size-validated
    // DTOs, so nothing upstream bounds them. Keycloak's default user profile
    // allows a username up to 255 while core_auth_user.username is VARCHAR(64)
    // and display_name is VARCHAR(128) — verified against the real DB that a
    // 70-char username INSERT is rejected with "value too long for type character
    // varying(64)". That exception escapes resolveBusinessUserId out through
    // CoreRequestContextFilter, so an ops person who creates a long-username user
    // in the KC admin console produces an account that logs in at Keycloak and
    // then 500s on EVERY API call with an opaque "Unhandled exception".

    @Test
    void provisionPath_overlongUsername_refusesInsteadOfBlowingUpOnInsert() {
        String tooLong = "u".repeat(65);   // one over VARCHAR(64)
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-long",
                "tid", "demo",
                "preferred_username", tooLong));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-long", "demo")).thenReturn(null);
        when(userMapper.findByUsernameAndTenant("demo", tooLong)).thenReturn(null);

        String businessId = service.resolveBusinessUserId(token);

        // Same graceful shape as the deleted-user branch: null, no insert.
        assertThat(businessId).isNull();
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    void provisionPath_maxLengthUsername_isStillProvisioned() {
        // The guard must reject only what the column cannot hold — exactly 64 fits.
        String exact = "u".repeat(64);
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-64",
                "tid", "demo",
                "preferred_username", exact));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-64", "demo")).thenReturn(null);
        when(userMapper.findByUsernameAndTenant("demo", exact)).thenReturn(null);

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isNotNull();
        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getUsername()).hasSize(64);
    }

    @Test
    void provisionPath_overlongDisplayName_isTruncatedNotRefused() {
        // display_name is cosmetic, and given_name + " " + family_name makes it the
        // easiest of the three to overflow — a login must not fail over it.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-disp",
                "tid", "demo",
                "preferred_username", "hank",
                "given_name", "G".repeat(100),
                "family_name", "F".repeat(100)));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-disp", "demo")).thenReturn(null);
        when(userMapper.findByUsernameAndTenant("demo", "hank")).thenReturn(null);

        String businessId = service.resolveBusinessUserId(token);

        assertThat(businessId).isNotNull();
        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getDisplayName())
                .as("clamped to core_auth_user.display_name's VARCHAR(128)")
                .hasSize(128);
        assertThat(cap.getValue().getUsername()).isEqualTo("hank");
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


    // ─── identity confusion on the bind path ─────────────────────────
    // The legacy-bind branch fed the IdP's preferred_username claim into
    // findByIdentifier, the LOGIN matcher (username OR email OR user_no).
    // Keycloak's registrationEmailAsUsername makes email-shaped usernames
    // ordinary, so that matcher could land on a DIFFERENT business user whose
    // *email* equals this Keycloak *username* — and the bind would then stamp
    // this token's keycloak_id onto that victim's row. From the next request on,
    // the fast path resolves the SSO user to the VICTIM's business user id
    // (their roles, their department, their data scope), and the same UPDATE
    // nulls the victim's password_hash unless they hold SUPER_ADMIN, destroying
    // their break-glass credential. (tenant_id, username) is uniquely indexed,
    // so an exact username lookup is both correct and unambiguous.

    @Test
    void bindPath_looksUpTheExactUsername_notTheLoginIdentifier() {
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-confuse",
                "tid", "demo",
                "preferred_username", "bob@example.com"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-confuse", "demo")).thenReturn(null);
        when(userMapper.findByUsernameAndTenant("demo", "bob@example.com")).thenReturn(null);

        service.resolveBusinessUserId(token);

        // The login matcher must never be consulted for a machine-to-row bind.
        verify(userMapper, never()).findByIdentifier(any(), any());
        verify(userMapper).findByUsernameAndTenant("demo", "bob@example.com");
    }

    @Test
    void bindPath_doesNotBindOntoAnotherUserWhoseEmailMatchesThisKeycloakUsername() {
        // No business user is named "bob@example.com"; only a DIFFERENT user
        // happens to carry it as their email. Nothing may be bound — the SSO
        // user is provisioned fresh instead.
        Jwt token = jwt(Map.of(
                "sub", "kc-uuid-confuse-2",
                "tid", "demo",
                "preferred_username", "bob@example.com"));
        when(userMapper.findByKeycloakIdAndTenant("kc-uuid-confuse-2", "demo")).thenReturn(null);
        when(userMapper.findByUsernameAndTenant("demo", "bob@example.com")).thenReturn(null);

        String businessId = service.resolveBusinessUserId(token);

        // No UPDATE at all → the other user's keycloak_id and password_hash are untouched.
        verify(userMapper, never()).update(any(), any());
        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getKeycloakId()).isEqualTo("kc-uuid-confuse-2");
        assertThat(businessId).isEqualTo(cap.getValue().getId());
    }

}
