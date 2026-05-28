package com.platform.system.auth.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the role-gating invariants of the break-glass self-service endpoint.
 * The whole reason this controller exists is to give super-admins a clean
 * way to manage their break-glass credential — letting a non-super-admin
 * call it would be a privilege-escalation footgun, so the guard MUST hold.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreakGlassControllerTest {

    @Mock UserMapper userMapper;
    @Mock RoleMapper roleMapper;
    @Mock com.platform.system.rbac.service.BuiltInRoleLookup roleLookup;
    @Mock PasswordEncoder encoder;
    @Mock PasswordPolicyService passwordPolicy;
    @InjectMocks BreakGlassController controller;

    @BeforeEach
    void setRequestContext() {
        RequestContext.set("demo", "ULID-CALLER", "alice", Locale.JAPAN, "test-trace");
        // requireSuperAdmin now resolves the demo super-admin role id via lookup;
        // mirror the pre-refactor "demo's SUPER_ADMIN_ID is the canonical answer".
        when(roleLookup.superAdminRoleId("demo")).thenReturn(BuiltInRoles.SUPER_ADMIN_ID);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }

    @Test
    void status_superAdmin_withConfiguredHash_returnsConfiguredTrue() {
        when(roleMapper.findRoleIdsByUserId("ULID-CALLER", "demo"))
                .thenReturn(List.of(BuiltInRoles.SUPER_ADMIN_ID));
        UserEntity row = new UserEntity();
        row.setId("ULID-CALLER");
        row.setPasswordHash("$2a$12$existinghash...");
        when(userMapper.selectById("ULID-CALLER")).thenReturn(row);

        var res = controller.status();

        assertThat(res.data().get("configured")).isEqualTo(true);
    }

    @Test
    void status_superAdmin_withNullHash_returnsConfiguredFalse() {
        when(roleMapper.findRoleIdsByUserId("ULID-CALLER", "demo"))
                .thenReturn(List.of(BuiltInRoles.SUPER_ADMIN_ID));
        UserEntity row = new UserEntity();
        row.setId("ULID-CALLER");
        row.setPasswordHash(null);
        when(userMapper.selectById("ULID-CALLER")).thenReturn(row);

        var res = controller.status();

        assertThat(res.data().get("configured")).isEqualTo(false);
    }

    @Test
    void status_nonSuperAdmin_refused() {
        when(roleMapper.findRoleIdsByUserId("ULID-CALLER", "demo")).thenReturn(List.of());

        assertThatThrownBy(() -> controller.status())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("super-admin");
    }

    @Test
    void status_unauthenticated_refused() {
        // No RequestContext.userId → endpoint must refuse before touching DB.
        RequestContext.clear();
        assertThatThrownBy(() -> controller.status())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    void setPassword_superAdmin_writesBcryptViaUpdateWrapper() {
        when(roleMapper.findRoleIdsByUserId("ULID-CALLER", "demo"))
                .thenReturn(List.of(BuiltInRoles.SUPER_ADMIN_ID));
        when(encoder.encode("NewBreakGlass!23")).thenReturn("$2a$12$newhash...");

        controller.setBreakGlassPassword(
                new BreakGlassController.SetBreakGlassRequest("NewBreakGlass!23"));

        verify(passwordPolicy).validate("NewBreakGlass!23");

        // Verify the UPDATE wrapper carries the right SET columns.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UserEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(isNull(), cap.capture());
        String sql = cap.getValue().getSqlSet();
        Map<String, Object> params = cap.getValue().getParamNameValuePairs();
        assertThat(sql).contains("password_hash=");
        assertThat(sql).contains("update_user=");
        assertThat(params.values()).contains("$2a$12$newhash...");
        assertThat(params.values()).contains("self-break-glass-set");
    }

    @Test
    void setPassword_nonSuperAdmin_refused_andDoesNotWriteToDb() {
        when(roleMapper.findRoleIdsByUserId("ULID-CALLER", "demo")).thenReturn(List.of());

        assertThatThrownBy(() ->
                controller.setBreakGlassPassword(
                        new BreakGlassController.SetBreakGlassRequest("NewPass!23")))
                .isInstanceOf(BusinessException.class);

        // Critical: the role check must short-circuit BEFORE any password
        // policy / encoder call lands. Otherwise a non-admin caller could
        // probe the password policy oracle (length / HIBP coverage / etc.)
        // via 400 vs 403 timing differentials.
        verify(passwordPolicy, never()).validate(any());
        verify(encoder, never()).encode(any(String.class));
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void setPassword_roleLookupFails_refusedDefensively() {
        // Opposite default from OidcJitUserService — break-glass guards a
        // WRITE on a security-sensitive column. A failed role check must
        // refuse, not allow (better to spuriously deny one admin than to
        // accidentally let a non-admin set a break-glass credential).
        when(roleMapper.findRoleIdsByUserId("ULID-CALLER", "demo"))
                .thenThrow(new RuntimeException("DB blip"));

        assertThatThrownBy(() ->
                controller.setBreakGlassPassword(
                        new BreakGlassController.SetBreakGlassRequest("NewPass!23")))
                .isInstanceOf(BusinessException.class);

        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void setPassword_passwordPolicyRejects_propagatesAndDoesNotWrite() {
        when(roleMapper.findRoleIdsByUserId("ULID-CALLER", "demo"))
                .thenReturn(List.of(BuiltInRoles.SUPER_ADMIN_ID));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "Pwned: seen in HIBP"))
                .when(passwordPolicy).validate("password123");

        assertThatThrownBy(() ->
                controller.setBreakGlassPassword(
                        new BreakGlassController.SetBreakGlassRequest("password123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pwned");

        verify(encoder, never()).encode(any(String.class));
        verify(userMapper, never()).update(any(), any());
    }
}
