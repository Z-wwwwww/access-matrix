package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.rbac.dto.UserDto;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
 *   2. The "last active SUPER_ADMIN" guard fires on delete / disable /
 *      role-strip and refers to the seeded {@link BuiltInRoles#SUPER_ADMIN_ID}
 *      (not a code/name lookup).
 *   3. Soft deletes go through {@code UpdateWrapper.set("mark", 0)} — the
 *      historical {@code setMark(0)+updateById} no-op'd because {@code @TableLogic}
 *      strips the field from BaseMapper's SET clause.
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock UserMapper userMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock RoleMapper roleMapper;
    @Mock PasswordEncoder encoder;
    @Mock PasswordPolicyService passwordPolicy;
    @Mock PermissionCacheService cacheService;
    @Mock ForceLogoutService forceLogoutService;
    @Mock NumberingService numberingService;
    // ObjectProvider mocks for the three OIDC-conditional beans + the mail
    // properties record. Default behaviour: getIfAvailable() returns null,
    // meaning the legacy / non-OIDC code path runs (no Keycloak side-effects,
    // no email send) — keeps these unit tests focused on the DB plumbing.
    @Mock ObjectProvider<KeycloakUserService> keycloakProvider;
    @Mock ObjectProvider<InviteTokenService> inviteProvider;
    @Mock ObjectProvider<MailService> mailProvider;
    AppMailProperties mailProps = new AppMailProperties(false, null, null, null);

    @InjectMocks UserAdminService service;

    @BeforeEach
    void seedTenant() {
        RequestContext.set("acme", "tester", "tester", Locale.JAPAN, "trace-1");
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

    private RoleEntity superAdminRole() {
        RoleEntity r = new RoleEntity();
        r.setId(BuiltInRoles.SUPER_ADMIN_ID);
        r.setName("Super Administrator");
        r.setIsBuiltIn(1);
        r.setMark(1);
        return r;
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
        // the service must fall back to "default" rather than NPE.
        RequestContext.clear();
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(encoder.encode(anyString())).thenReturn("HASHED");
        when(numberingService.next("USER", "default")).thenReturn("U00000099");

        UserDto.CreateRequest req = new UserDto.CreateRequest(
                "bob", "Password!23", "bob@example.com", "Bob", null, 1,
                UserDto.ProvisionMode.DIRECT);
        service.create(req);

        verify(numberingService).next("USER", "default");
    }

    @Test
    void delete_softDeletesViaUpdateWrapper_andKicksOut() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        // not super admin
        when(roleMapper.selectById(BuiltInRoles.SUPER_ADMIN_ID)).thenReturn(superAdminRole());
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);

        service.delete("u1");

        // Both user + user_role soft-deletes go through UpdateWrapper, not setMark+updateById.
        ArgumentCaptor<UpdateWrapper<UserEntity>> userCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(eq(null), userCap.capture());
        assertThat(userCap.getValue().getSqlSet()).contains("mark=");
        assertThat(userCap.getValue().getParamNameValuePairs().values()).contains(0);

        verify(userRoleMapper).update(eq(null), any(UpdateWrapper.class));
        verify(cacheService).evictUser("u1");
        // Tokens must die immediately on user delete — otherwise the deleted
        // user can keep hitting endpoints until their token naturally expires.
        verify(forceLogoutService).kickOut("u1");
    }

    @Test
    void delete_refusesBuiltInAdmin() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "admin"));

        assertThatThrownBy(() -> service.delete("u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Built-in admin");

        verify(userMapper, never()).update(any(), any());
        verify(forceLogoutService, never()).kickOut(any());
    }

    @Test
    void delete_refusesLastSuperAdmin() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(roleMapper.selectById(BuiltInRoles.SUPER_ADMIN_ID)).thenReturn(superAdminRole());
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);
        when(userRoleMapper.countActiveHoldersByRoleId(BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1L);

        assertThatThrownBy(() -> service.delete("u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last active SUPER_ADMIN");

        // No soft-delete or token kick when we bail out early.
        verify(userMapper, never()).update(any(), any());
        verify(forceLogoutService, never()).kickOut(any());
    }

    @Test
    void delete_allowsSuperAdminWhenNotLast() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(roleMapper.selectById(BuiltInRoles.SUPER_ADMIN_ID)).thenReturn(superAdminRole());
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);
        when(userRoleMapper.countActiveHoldersByRoleId(BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(3L);

        service.delete("u1");

        verify(userMapper).update(eq(null), any(UpdateWrapper.class));
        verify(forceLogoutService).kickOut("u1");
    }

    @Test
    void changeStatus_disablingTriggersKickOut() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(roleMapper.selectById(BuiltInRoles.SUPER_ADMIN_ID)).thenReturn(superAdminRole());
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(null);

        service.changeStatus("u1", 0);

        // Disabling must immediately invalidate tokens, not just on the next perm check.
        verify(forceLogoutService).kickOut("u1");
        verify(cacheService).evictUser("u1");
    }

    @Test
    void changeStatus_enablingDoesNotKickOut() {
        // Enabling a disabled super-admin is always safe — no last-admin check, no token kick.
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));

        service.changeStatus("u1", 1);

        verify(forceLogoutService, never()).kickOut(any());
    }

    @Test
    void assignRoles_refusesStrippingSuperFromLastSuperAdmin() {
        // The role-strip path must trip the same last-admin guard as delete / disable —
        // otherwise an admin could silently leave the platform with no super admins.
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(roleMapper.selectById(BuiltInRoles.SUPER_ADMIN_ID)).thenReturn(superAdminRole());
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);
        when(userRoleMapper.countActiveHoldersByRoleId(BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1L);

        assertThatThrownBy(() -> service.assignRoles("u1", List.of("some-other-role-id")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("strip SUPER_ADMIN");

        verify(userRoleMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void assignRoles_allowsKeepingSuperRole() {
        when(userMapper.selectById("u1")).thenReturn(user("u1", "alice"));
        when(roleMapper.selectById(BuiltInRoles.SUPER_ADMIN_ID)).thenReturn(superAdminRole());
        when(userRoleMapper.existsActiveLink("u1", BuiltInRoles.SUPER_ADMIN_ID, "acme")).thenReturn(1);
        // Keeping the super role in the new set — guard should be skipped, no countActiveHoldersByRoleId call.

        service.assignRoles("u1", List.of(BuiltInRoles.SUPER_ADMIN_ID, "other-role"));

        verify(userRoleMapper).update(eq(null), any(UpdateWrapper.class)); // unlink-all step
        verify(userRoleMapper, never()).countActiveHoldersByRoleId(any(), any());
    }
}
