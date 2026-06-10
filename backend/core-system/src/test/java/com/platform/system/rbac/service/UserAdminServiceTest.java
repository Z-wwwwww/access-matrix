package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.system.auth.service.SessionTerminationService;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.rbac.dto.UserDto;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins three non-obvious UserAdminService invariants the repo has tripped on
 * before:
 *
 *   1. {@code userNo} numbering is keyed by the *current* tenant — multi-tenant
 *      installs need per-tenant counters (USER + tenantId), not a global one.
 *   2. Protected admins (built-in admin / tenant SUPER_ADMIN, resolved via
 *      the seeded {@link BuiltInRoles#SUPER_ADMIN_ID} — not a code/name
 *      lookup) are fully read-only in the console: update / role-assign /
 *      dept / status / delete / reset / kick are all refused, whoever calls.
 *   3. Soft deletes go through {@code UpdateWrapper.set("mark", 0)} — the
 *      historical {@code setMark(0)+updateById} no-op'd because {@code @TableLogic}
 *      strips the field from BaseMapper's SET clause.
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock UserMapper userMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock com.platform.system.rbac.service.BuiltInRoleLookup roleLookup;
    @Mock PasswordEncoder encoder;
    @Mock PasswordPolicyService passwordPolicy;
    @Mock PermissionCacheService cacheService;
    @Mock SessionTerminationService sessionTermination;
    @Mock NumberingService numberingService;
    // ObjectProvider mocks for the three OIDC-conditional beans + the mail
    // properties record. Default behaviour: getIfAvailable() returns null,
    // meaning the legacy / non-OIDC code path runs (no Keycloak side-effects,
    // no email send) — keeps these unit tests focused on the DB plumbing.
    @Mock ObjectProvider<KeycloakUserService> keycloakProvider;
    @Mock ObjectProvider<InviteTokenService> inviteProvider;
    @Mock ObjectProvider<MailService> mailProvider;
    AppMailProperties mailProps = new AppMailProperties(false, null, null, null);

    UserAdminService service;

    @BeforeEach
    void seedTenant() {
        // Manual construction instead of @InjectMocks: the three ObjectProvider
        // mocks share the same erased type, so Mockito's constructor injection
        // can shuffle them between slots (the keycloak slot silently getting the
        // invite provider, etc.). Explicit args pin each provider to its slot.
        service = new UserAdminService(userMapper, userRoleMapper, roleLookup, encoder,
                passwordPolicy, cacheService, sessionTermination, numberingService,
                keycloakProvider, inviteProvider, mailProvider, mailProps);
        RequestContext.set("acme", "tester", "tester", Locale.JAPAN, "trace-1");
        // Tests run as tenant=acme; pre-refactor stubs used the constant
        // SUPER_ADMIN_ID directly. After the lookup refactor, the service
        // resolves the tenant's super-admin role id via BuiltInRoleLookup —
        // mirror that resolution here so the existsActiveLink / count
        // queries keyed on SUPER_ADMIN_ID still match.
        org.mockito.Mockito.lenient()
                .when(roleLookup.superAdminRoleId("acme"))
                .thenReturn(BuiltInRoles.SUPER_ADMIN_ID);
    }

    @AfterEach
    void clearTenant() {
        RequestContext.clear();
    }

    private UserEntity user(String id, String username) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setMark(1);
        u.setStatus(1);
        return u;
    }

    @Test
    void create_passesCurrentTenantToNumberingService() {
        // The seeded numbering counter is keyed (kbn, tenantId) — passing a wrong
        // tenant would silently collide with another tenant's user-no sequence.
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(encoder.encode(anyString())).thenReturn("HASHED");
        when(numberingService.next("USER", "acme")).thenReturn("U00000001");

        UserDto.CreateRequest req = new UserDto.CreateRequest(
                "alice", "Password!23", "alice@example.com", "Alice", null, 1,
                UserDto.ProvisionMode.DIRECT);
        String id = service.create(req);

        assertThat(id).isNotBlank();
        verify(numberingService).next("USER", "acme");
        verify(userMapper).insert(any(UserEntity.class));
    }

    @Test
    void create_fallsBackToDefaultTenantWhenContextEmpty() {
        // RequestContext.tenantId() can be null in local / batch / test paths —
        // the service must fall back to "demo" rather than NPE.
        RequestContext.clear();
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(encoder.encode(anyString())).thenReturn("HASHED");
        when(numberingService.next("USER", "demo")).thenReturn("U00000099");

        UserDto.CreateRequest req = new UserDto.CreateRequest(
                "bob", "Password!23", "bob@example.com", "Bob", null, 1,
                UserDto.ProvisionMode.DIRECT);
        service.create(req);

        verify(numberingService).next("USER", "demo");
    }

    @Test
    void list_withRoleFilter_addsParameterizedExistsClause() {
        // The role filter must be a BOUND subquery (no string concat — roleId
        // is client input) against active user_role links, with the outer id
        // table-qualified so the link table's own `id` doesn't shadow it.
        Page<UserEntity> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(userMapper.selectPage(any(), any())).thenReturn(page);
        when(userRoleMapper.findUserIdsByRoleId(BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(List.of());

        service.list(1, 20, null, null, "role-1");

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserEntity>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(userMapper).selectPage(any(), cap.capture());
        assertThat(cap.getValue().getSqlSegment()).contains("EXISTS");
        assertThat(cap.getValue().getSqlSegment()).contains("ur.user_id = core_auth_user.id");
        assertThat(cap.getValue().getParamNameValuePairs().values()).contains("role-1", "acme");
    }

    @Test
    void delete_softDeletesViaUpdateWrapper_andKicksOut() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        // not super admin
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);

        service.delete("u1");

        // Both user + user_role soft-deletes go through UpdateWrapper, not setMark+updateById.
        ArgumentCaptor<UpdateWrapper<UserEntity>> userCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(eq(null), userCap.capture());
        assertThat(userCap.getValue().getSqlSet()).contains("mark=");
        assertThat(userCap.getValue().getParamNameValuePairs().values()).contains(0);
        // Linchpin invariant: the soft-delete must NOT touch keycloak_id. The
        // retained (mark=0, keycloak_id) "tombstone" is what OIDC JIT reads to
        // refuse re-provisioning a deleted user (countDeletedByKeycloakIdAndTenant).
        // Null it / purge the row and a stale token would re-land as a ghost.
        assertThat(userCap.getValue().getSqlSet()).doesNotContain("keycloak_id");

        verify(userRoleMapper).update(eq(null), any(UpdateWrapper.class));
        verify(cacheService).evictUser("u1");
        // Tokens must die immediately on user delete — otherwise the deleted
        // user can keep hitting endpoints until their token naturally expires.
        verify(sessionTermination).terminateUser("u1");
    }

    @Test
    void delete_refusesBuiltInAdmin() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "demo-admin"));

        assertThatThrownBy(() -> service.delete("u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");

        verify(userMapper, never()).update(any(), any());
        verify(sessionTermination, never()).terminateUser(any());
    }

    // ─── protected admins are fully read-only in this console ──────────
    // The built-in admin and the tenant's singular SUPER_ADMIN cannot be
    // edited by ANY other user, whatever user-management permission they
    // hold — including contact fields. The admin edits their own info via
    // the Profile page (updateOwnProfile).

    @Test
    void update_refusesBuiltInAdmin() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "demo-admin"));

        assertThatThrownBy(() -> service.update("u1", new UserDto.UpdateRequest(
                "admin-new@platform.local", "Local Admin", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");

        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void update_refusesTenantSuperAdmin() {
        // The regression this pins: a user holding user:update could edit the
        // tenant admin's email / display name. Now the SUPER_ADMIN holder is
        // wholly off-limits, even for contact fields.
        when(userMapper.selectById("u1")).thenReturn(user("u1", "owner"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);

        assertThatThrownBy(() -> service.update("u1", new UserDto.UpdateRequest(
                "owner-new@example.com", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");

        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void changeDept_refusesTenantSuperAdmin() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "owner"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);

        assertThatThrownBy(() -> service.changeDept("u1", "DEPT-OTHER"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");

        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void changeStatus_refusesTenantSuperAdmin_bothDirections() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "owner"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);

        assertThatThrownBy(() -> service.changeStatus("u1", 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");
        assertThatThrownBy(() -> service.changeStatus("u1", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");

        verify(sessionTermination, never()).applyEnabled(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // ─── KC profile sync on email / displayName edits ──────────────────
    // Both the admin console edit and the self-service Profile page must
    // mirror contact changes into Keycloak — otherwise KC keeps the old
    // email and its "forgot password" flow mails the stale address.

    @Test
    void update_syncsEmailAndDisplayNameToKeycloak() {
        UserEntity u = user("u1", "alice");
        u.setKeycloakId("kc-1");
        when(userMapper.selectById("u1")).thenReturn(u);
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);
        KeycloakUserService kc = org.mockito.Mockito.mock(KeycloakUserService.class);
        when(keycloakProvider.getIfAvailable()).thenReturn(kc);

        service.update("u1", new UserDto.UpdateRequest(
                "alice-new@example.com", "Alice New", null, null));

        verify(kc).updateProfile("acme", "kc-1", "alice-new@example.com", "Alice New");
        verify(userMapper).updateById(u);
    }

    @Test
    void update_keycloakFailureLeavesDbUntouched() {
        // KC-first ordering (same as create): if the KC mirror fails, the
        // local row must NOT be written — otherwise DB and KC diverge and
        // the operator gets no signal.
        UserEntity u = user("u1", "alice");
        u.setKeycloakId("kc-1");
        when(userMapper.selectById("u1")).thenReturn(u);
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);
        KeycloakUserService kc = org.mockito.Mockito.mock(KeycloakUserService.class);
        when(keycloakProvider.getIfAvailable()).thenReturn(kc);
        org.mockito.Mockito.doThrow(new KeycloakUserService.KeycloakOperationException("boom"))
                .when(kc).updateProfile(anyString(), anyString(), any(), any());

        assertThatThrownBy(() -> service.update("u1", new UserDto.UpdateRequest(
                "alice-new@example.com", null, null, null)))
                .isInstanceOf(KeycloakUserService.KeycloakOperationException.class);

        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void update_refusesDuplicateEmail() {
        // Email doubles as a login identifier (findByIdentifier … LIMIT 1) —
        // the edit paths need the same precise pre-check as create, or in
        // legacy mode a duplicate lands in the DB and email login turns
        // ambiguous. Checked BEFORE the KC mirror so KC is never touched.
        UserEntity u = user("u1", "alice");
        u.setKeycloakId("kc-1");
        when(userMapper.selectById("u1")).thenReturn(u);
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.update("u1", new UserDto.UpdateRequest(
                "taken@example.com", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.emailExists");

        verify(userMapper, never()).updateById(any(UserEntity.class));
        verify(keycloakProvider, never()).getIfAvailable();
    }

    @Test
    void updateOwnProfile_refusesDuplicateEmail() {
        UserEntity me = user("tester", "tester");
        when(userMapper.selectById("tester")).thenReturn(me);
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.updateOwnProfile(
                new UserDto.ProfileUpdateRequest("taken@example.com", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.emailExists");

        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void updateOwnProfile_syncsToKeycloak() {
        // RequestContext userId is "tester" (seeded above) — the Profile page
        // edits the caller's OWN row.
        UserEntity me = user("tester", "tester");
        me.setKeycloakId("kc-me");
        when(userMapper.selectById("tester")).thenReturn(me);
        KeycloakUserService kc = org.mockito.Mockito.mock(KeycloakUserService.class);
        when(keycloakProvider.getIfAvailable()).thenReturn(kc);

        service.updateOwnProfile(new UserDto.ProfileUpdateRequest("me-new@example.com", "Me New"));

        verify(kc).updateProfile("acme", "kc-me", "me-new@example.com", "Me New");
        verify(userMapper).updateById(me);
    }

    @Test
    void delete_refusesTenantSuperAdmin() {
        // Subsumes the old "last active SUPER_ADMIN" guard: the holder is a
        // protected admin and can never be deleted, so the tenant always
        // keeps its one super admin.
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);

        assertThatThrownBy(() -> service.delete("u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");

        // No soft-delete or token kick when we bail out early.
        verify(userMapper, never()).update(any(), any());
        verify(sessionTermination, never()).terminateUser(any());
    }

    @Test
    void changeStatus_disable_appliesDisabledState() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);

        service.changeStatus("u1", 0);

        // All session/KC side-effects of "disabled" are owned by SessionTerminationService
        // (kick + KC user disable + end session — tested in SessionTerminationServiceTest).
        verify(sessionTermination).applyEnabled("u1", false);
        verify(cacheService).evictUser("u1");
    }

    @Test
    void changeStatus_enable_appliesEnabledState() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);

        service.changeStatus("u1", 1);

        verify(sessionTermination).applyEnabled("u1", true);
    }

    // ─── admin password reset (aligned with the platform-user console) ──
    // The admin never types a password: the service rotates a generated temp
    // password (KC temporary=true in OIDC mode / local hash in legacy mode),
    // terminates the target's sessions, and returns the temp password once.

    @Test
    void resetPassword_oidc_rotatesKeycloakTempPassword_andTerminatesSessions() {
        UserEntity u = user("u1", "alice");
        u.setKeycloakId("kc-1");
        when(userMapper.selectById("u1")).thenReturn(u);
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);
        KeycloakUserService kc = org.mockito.Mockito.mock(KeycloakUserService.class);
        when(keycloakProvider.getIfAvailable()).thenReturn(kc);

        UserDto.ResetPwResponse res = service.resetPassword("u1");

        assertThat(res.tempPassword()).hasSize(16);
        assertThat(res.username()).isEqualTo("alice");
        // OIDC mode: the credential lives in Keycloak (realm = tenant), marked
        // temporary so KC forces the user to pick their own on next login. The
        // local password_hash must stay untouched ("as-if-always-OIDC").
        verify(kc).setPassword(eq("acme"), eq("kc-1"), eq(res.tempPassword()), eq(true));
        verify(userMapper, never()).updateById(any(UserEntity.class));
        // Reset must evict the (possibly hijacked) current session holder.
        verify(sessionTermination).terminateUser("u1");
    }

    @Test
    void resetPassword_legacyMode_writesLocalHash() {
        UserEntity u = user("u1", "alice");
        when(userMapper.selectById("u1")).thenReturn(u);
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);
        when(encoder.encode(anyString())).thenReturn("HASHED");

        UserDto.ResetPwResponse res = service.resetPassword("u1");

        assertThat(res.tempPassword()).hasSize(16);
        assertThat(u.getPasswordHash()).isEqualTo("HASHED");
        verify(userMapper).updateById(u);
        verify(sessionTermination).terminateUser("u1");
    }

    @Test
    void resetPassword_refusesProtectedAdmin() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "demo-admin"));

        assertThatThrownBy(() -> service.resetPassword("u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");

        verify(sessionTermination, never()).terminateUser(any());
    }

    @Test
    void resetPassword_refusesSelf() {
        // RequestContext userId is "tester" (seeded above) — resetting your own
        // password from the admin console is a self-management footgun; the
        // sanctioned self path is the KC account console.
        assertThatThrownBy(() -> service.resetPassword("tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.selfManagementForbidden");
    }

    @Test
    void assignRoles_refusesTenantSuperAdminTarget() {
        // The SUPER_ADMIN holder is a protected admin: nobody can touch their
        // role bindings (which also makes stripping the super role impossible).
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);

        assertThatThrownBy(() -> service.assignRoles("u1", List.of("some-other-role-id")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.adminProtected");

        verify(userRoleMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void assignRoles_refusesGrantingSuperToSecondUser() {
        // SUPER_ADMIN is singular — it can never be granted to a second user.
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);

        assertThatThrownBy(() -> service.assignRoles("u1", List.of(BuiltInRoles.SUPER_ADMIN_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.user.superAdminSingleton");

        verify(userRoleMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void assignRoles_allowsNormalUserRoleChange() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);

        service.assignRoles("u1", List.of("other-role"));

        verify(userRoleMapper).update(eq(null), any(UpdateWrapper.class)); // unlink-all step
        verify(userRoleMapper).insert(any(UserRoleEntity.class));
        verify(cacheService).evictUser("u1");
    }
}
