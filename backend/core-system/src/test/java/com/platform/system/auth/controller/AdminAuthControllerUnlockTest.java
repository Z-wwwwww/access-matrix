package com.platform.system.auth.controller;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.infrastructure.security.AccountLockoutService;
import com.platform.system.auth.dto.UnlockRequest;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.service.UserAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /admin/auth/unlock} must clear the LOCKOUT and nothing else.
 *
 * <p>It used to also run {@code user.setStatus(1); userMapper.updateById(user)}.
 * That could never be part of unlocking — lockout state lives entirely in Redis
 * ({@code AccountLockoutService} writes {@code auth:fail:*} / {@code auth:lock:*}
 * and nothing else; no code path sets {@code status = 0} on failed logins, and
 * {@code AuthService.login} rejects locked vs disabled as two independent checks).
 * The only state it could ever flip was an account an admin had DELIBERATELY
 * disabled, and it did that:
 * <ul>
 *   <li>without {@code assertNotProtectedAdmin} — so it could re-enable the
 *       built-in admin / the tenant SUPER_ADMIN, which every other write refuses;</li>
 *   <li>without {@code assertNotSelf};</li>
 *   <li>without {@code SessionTerminationService.applyEnabled} — so Keycloak was
 *       NOT re-enabled and the force-logout kick was NOT cleared, leaving the DB
 *       "enabled" while KC still refused the login. That DB/KC divergence is
 *       exactly what SessionTerminationService was introduced to stop.</li>
 * </ul>
 * Re-enabling an account is {@code PUT /admin/user/{id}/status} ({@code user:update}),
 * not a side effect of the narrower {@code auth:unlock} permission.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthControllerUnlockTest {

    @Mock UserMapper userMapper;
    @Mock AccountLockoutService lockoutService;
    @Mock UserAdminService userAdminService;

    AdminAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAuthController(userMapper, lockoutService, userAdminService);
        RequestContext.set("acme", "admin-id", "admin", Locale.JAPAN, "trace-1");
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    private static UserEntity user(int status) {
        UserEntity u = new UserEntity();
        u.setId("ULID-U1");
        u.setTenantId("acme");
        u.setUsername("alice");
        u.setStatus(status);
        u.setMark(1);
        return u;
    }

    @Test
    void unlock_clearsTheLockoutAndDoesNotWriteTheUserRow() {
        when(userMapper.findByIdentifier("acme", "alice")).thenReturn(user(1));

        controller.unlock(new UnlockRequest("alice"));

        verify(lockoutService).reset("acme", "alice");
        verify(userMapper, never()).updateById(any(UserEntity.class));
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void unlock_doesNotSilentlyReEnableADeliberatelyDisabledAccount() {
        // status=0 means an admin disabled this account (KC user disabled + kicked).
        // Unlocking must not undo that behind the guards' back.
        UserEntity disabled = user(0);
        when(userMapper.findByIdentifier("acme", "alice")).thenReturn(disabled);

        controller.unlock(new UnlockRequest("alice"));

        verify(lockoutService).reset("acme", "alice");
        verify(userMapper, never()).updateById(any(UserEntity.class));
        org.assertj.core.api.Assertions.assertThat(disabled.getStatus())
                .as("the disabled flag stays untouched")
                .isEqualTo(0);
    }

    @Test
    void unlock_stillRejectsAnUnknownUsername() {
        when(userMapper.findByIdentifier("acme", "ghost")).thenReturn(null);

        assertThatThrownBy(() -> controller.unlock(new UnlockRequest("ghost")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");

        verify(lockoutService, never()).reset(any(), any());
    }

    @Test
    void unlock_isIdempotentForAnAccountThatWasNeverLocked() {
        when(userMapper.findByIdentifier("acme", "alice")).thenReturn(user(1));

        assertThatCode(() -> {
            controller.unlock(new UnlockRequest("alice"));
            controller.unlock(new UnlockRequest("alice"));
        }).doesNotThrowAnyException();
    }
}
