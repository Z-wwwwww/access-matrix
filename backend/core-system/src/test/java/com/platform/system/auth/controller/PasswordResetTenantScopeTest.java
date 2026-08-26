package com.platform.system.auth.controller;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.infrastructure.audit.OpLogSink;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.security.ClientIpResolver;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.PasswordResetTokenEntity;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.auth.service.PasswordResetTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The post-consume half of the reset flow must run under the tenant the TOKEN
 * names, not the one the request header carries.
 *
 * <p>MyBatis-Plus rewrites ALL SQL — hand-written {@code @Select} included — so
 * every statement after {@code tokens.consume(...)} silently receives
 * {@code AND tenant_id = <RequestContext.tenantId()>}. On this PRE-AUTH endpoint
 * that value is {@code X-Tenant-Id}, which the SPA derives from the subdomain,
 * while the reset link comes from the single global {@code app.mail.base-url}: a
 * first-time recipient (nothing in localStorage, apex / reserved host) resolves it
 * to the {@code demo} fallback. {@code PasswordResetTokenMapper} documents exactly
 * that scenario and carries {@code @InterceptorIgnore} for it — but only the token
 * lookup was covered. The next two statements were not:
 *
 * <ul>
 *   <li>{@code findByIdAndTenant(<user>, "sozonext")} plus an injected
 *       {@code tenant_id = 'demo'} contradicts → null → NOT_FOUND thrown AFTER the
 *       single-use token was already burned. The link is dead; only an operator can
 *       mint another.</li>
 *   <li>Had it found the user, the password UPDATE would have matched 0 rows while
 *       the endpoint still returned 200, disabled the Keycloak identity and wrote a
 *       success audit row — no password anywhere, and locked out of both paths.</li>
 * </ul>
 *
 * <p>Mockito can't run the interceptor, so the assertions are on the thing the
 * interceptor reads: {@code RequestContext.tenantId()} at the moment each mapper
 * call is made. Plus the affected-row guard, which is what turns the second bullet
 * from a silent success into a refusal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetTenantScopeTest {

    /** Tenant the reset token (and therefore the user) actually belongs to. */
    private static final String TOKEN_TENANT = "sozonext";
    /** What X-Tenant-Id resolves to for a cold browser on the apex host. */
    private static final String HEADER_TENANT = "demo";

    @Mock OpLogSink opLogSink;
    @Mock ClientIpResolver clientIpResolver;
    @Mock PasswordPolicyService passwordPolicy;
    @Mock AppMailProperties mailProps;
    @Mock HttpServletRequest http;
    @Mock PasswordResetTokenService tokens;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder encoder;
    @Mock KeycloakUserService kc;

    private ObjectProvider<KeycloakUserService> kcProvider;
    /** Tenant in force at each mapper call, in call order. */
    private final List<String> tenantAtCall = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kcProvider = (ObjectProvider<KeycloakUserService>) mock(ObjectProvider.class);
        when(kcProvider.getIfAvailable()).thenReturn(kc);
        when(mailProps.baseUrl()).thenReturn("https://app.test");
        when(encoder.encode(anyString())).thenReturn("$2a$hash");

        PasswordResetTokenEntity row = new PasswordResetTokenEntity();
        row.setTenantId(TOKEN_TENANT);
        row.setUserId("ULID-USER");
        row.setKeycloakId("kc-uuid");
        when(tokens.consume("TOKEN")).thenReturn(row);

        UserEntity user = new UserEntity();
        user.setId("ULID-USER");
        user.setTenantId(TOKEN_TENANT);
        user.setUsername("alice");
        user.setMark(1);
        when(userMapper.findByIdAndTenant("ULID-USER", TOKEN_TENANT)).thenAnswer(inv -> {
            tenantAtCall.add(RequestContext.tenantId());
            return user;
        });

        // The browser that opened the emailed link resolved a DIFFERENT tenant.
        RequestContext.set(HEADER_TENANT, null, null, Locale.JAPAN, "trace-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private PasswordResetController controller() {
        return new PasswordResetController(tokens, userMapper, encoder, passwordPolicy,
                kcProvider, mailProps, opLogSink, clientIpResolver);
    }

    private void accept() {
        controller().accept("TOKEN",
                new PasswordResetController.ResetPasswordRequest("Str0ng!pass"), http);
    }

    @Test
    void bothMapperCallsRunUnderTheTokensTenant_notTheHeaders() {
        when(userMapper.update(any(), any())).thenAnswer(inv -> {
            tenantAtCall.add(RequestContext.tenantId());
            return 1;
        });

        accept();

        assertThat(tenantAtCall)
                .as("lookup and UPDATE both scoped to the token's tenant")
                .containsExactly(TOKEN_TENANT, TOKEN_TENANT);
    }

    @Test
    void theCallersContextIsRestoredAfterwards() {
        when(userMapper.update(any(), any())).thenReturn(1);

        accept();

        assertThat(RequestContext.tenantId()).isEqualTo(HEADER_TENANT);
        assertThat(RequestContext.current().getTraceId()).isEqualTo("trace-1");
    }

    @Test
    void aWriteThatMatchedNoRowIsRefused_andLeavesKeycloakAlone() {
        when(userMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(this::accept)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.passwordReset.notApplied");

        // Disabling the KC identity on top of a password that was never written
        // would leave the user with no way in at all.
        verify(kc, never()).disableUser(anyString(), anyString());
        verify(opLogSink, never()).record(any());
        // And the context is still unwound on the failure path.
        assertThat(RequestContext.tenantId()).isEqualTo(HEADER_TENANT);
    }
}
