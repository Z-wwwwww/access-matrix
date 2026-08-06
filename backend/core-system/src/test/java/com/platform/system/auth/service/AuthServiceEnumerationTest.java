package com.platform.system.auth.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.security.AccountLockoutService;
import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.JwtIssuer;
import com.platform.core.infrastructure.security.RefreshTokenStore;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.service.PermissionQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A wrong password must look the same whether or not the account exists.
 *
 * <p>The timing side of this is already handled — {@code login} runs a dummy
 * BCrypt compare for an unknown identifier precisely so the two branches take
 * the same time. But the LOCKOUT side leaked the same fact: failures were only
 * counted for identifiers that resolved to a row, so after {@code maxFailures}
 * attempts a real username started answering {@code ACCOUNT_LOCKED} ("try again
 * in N seconds") while a made-up one kept answering {@code BAD_CREDENTIALS}
 * forever. That difference is a username-enumeration oracle: send maxFailures+1
 * wrong passwords per candidate and read off which ones exist.
 *
 * <p>It also meant the unknown-identifier path consumed no lockout budget at
 * all, so brute-forcing "does this account exist" was free of the throttle that
 * exists for exactly that traffic.
 *
 * <p>Both branches now count the failure and both consult the lock before
 * branching on existence.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceEnumerationTest {

    @Mock UserMapper userMapper;
    @Mock PasswordEncoder encoder;
    @Mock JwtIssuer jwtIssuer;
    @Mock RefreshTokenStore refreshStore;
    @Mock AccountLockoutService lockoutService;
    @Mock LoginAuditService auditService;
    @Mock PermissionQueryService permissionQueryService;
    @Mock RoleMapper roleMapper;
    @Mock com.platform.system.rbac.service.BuiltInRoleLookup roleLookup;
    @Mock ForceLogoutService forceLogoutService;
    @Mock com.platform.system.platform.mapper.TenantMapper tenantMapper;
    @Mock MailService mailService;
    @Mock AppMailProperties mailProps;
    @Mock com.platform.core.infrastructure.audit.OpLogSink opLogSink;
    @Mock com.platform.core.infrastructure.security.ClientIpResolver clientIpResolver;

    @InjectMocks AuthService service;

    private HttpServletRequest req;

    private static final String UNKNOWN = "no-such-user";

    @BeforeEach
    void setUp() {
        req = mock(HttpServletRequest.class);
        when(req.getHeader("User-Agent")).thenReturn("test-agent/1.0");
        when(clientIpResolver.resolve(any())).thenReturn("10.0.0.1");
        when(mailProps.fromName()).thenReturn("Access Matrix");
        when(mailProps.from()).thenReturn("noreply@example.com");
        when(mailProps.baseUrl()).thenReturn("https://app.example.com");
        RequestContext.set("demo", null, null, Locale.JAPAN, "test-trace");
        com.platform.system.platform.entity.TenantEntity activeTenant =
                new com.platform.system.platform.entity.TenantEntity();
        activeTenant.setStatus(1);
        when(tenantMapper.findActiveByCode(anyString())).thenReturn(activeTenant);
        // The identifier resolves to nothing — the enumeration candidate.
        when(userMapper.findByIdentifier(eq("demo"), eq(UNKNOWN))).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void unknownIdentifierAlsoCountsTowardsTheLockout() {
        when(lockoutService.remainingLockSeconds("demo", UNKNOWN)).thenReturn(0L);

        assertThatThrownBy(() -> service.login(UNKNOWN, "wrong", req))
                .isInstanceOf(BusinessException.class);

        verify(lockoutService, org.mockito.Mockito.times(1))
                .recordFailure("demo", UNKNOWN);
    }

    @Test
    void aLockedUnknownIdentifierAnswersLockedJustLikeARealOne() {
        // Once the identifier is locked, the answer must be ACCOUNT_LOCKED even
        // though no such user exists — otherwise the response distinguishes them.
        when(lockoutService.remainingLockSeconds("demo", UNKNOWN)).thenReturn(42L);

        assertThatThrownBy(() -> service.login(UNKNOWN, "wrong", req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }

    @Test
    void aRealAccountWithABadPasswordStillBehavesTheSameWay() {
        // Control: the known-user branch is unchanged.
        UserEntity u = new UserEntity();
        u.setId("01JKNOWNUSER00000000000000");
        u.setUsername("alice");
        u.setTenantId("demo");
        u.setStatus(1);
        u.setPasswordHash("$2a$12$hash");
        when(userMapper.findByIdentifier(eq("demo"), eq("alice"))).thenReturn(u);
        when(lockoutService.remainingLockSeconds("demo", "alice")).thenReturn(0L);
        when(encoder.matches(anyString(), eq("$2a$12$hash"))).thenReturn(false);

        assertThatThrownBy(() -> service.login("alice", "wrong", req))
                .isInstanceOf(BusinessException.class);

        verify(lockoutService).recordFailure("demo", "alice");
    }
}
