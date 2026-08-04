package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four PRE-AUTH, token-keyed statements must carry
 * {@code @InterceptorIgnore(tenantLine = "true")}.
 *
 * <p>MyBatis-Plus rewrites ALL SQL — including hand-written {@code @Select} /
 * {@code @Update} — so without the annotation each of these gets
 * {@code AND tenant_id = <RequestContext.tenantId()>} appended.
 * {@code TenantMapper.findActiveByCode} carries the same annotation for the same
 * reason, and its javadoc spells the mechanism out; these four were left relying
 * on a javadoc claim ("hand-written, so we don't have a tenant in RequestContext")
 * that hand-writing does not actually buy.
 *
 * <p>Why it broke: the context tenant on a pre-auth request comes from the
 * {@code X-Tenant-Id} header, which the SPA derives from the SUBDOMAIN — but the
 * email link is built from the single global {@code app.mail.base-url}, so a
 * first-time recipient (nothing in localStorage, apex/reserved host) resolves to
 * the {@code demo} fallback. Verified against the real DB that a {@code sozonext}
 * invite is found by the intended SQL and by 0 rows once {@code tenant_id = 'demo'}
 * is appended, and that the claim UPDATE then affects 0 rows — which callers must
 * read as "already used". Net effect: every invite / password-reset link outside
 * the {@code demo} tenant reported "invalid or expired".
 *
 * <p>Structural test because the failure is invisible to a mocked-mapper unit test
 * (the interceptor only exists in a real MyBatis pipeline) and invisible in dev/QA
 * (which run as {@code demo}).
 */
class PreAuthTokenMapperScopingTest {

    private static Method method(Class<?> mapper, String name) {
        return java.util.Arrays.stream(mapper.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(mapper.getSimpleName() + "#" + name + " is gone"));
    }

    private static void assertIgnoresTenantLine(Class<?> mapper, String name) {
        InterceptorIgnore ann = method(mapper, name).getAnnotation(InterceptorIgnore.class);
        assertThat(ann)
                .as("%s#%s is pre-auth and keyed on the globally-unique token hash; "
                        + "without @InterceptorIgnore the tenant interceptor appends "
                        + "tenant_id = <request tenant> and the row is invisible",
                        mapper.getSimpleName(), name)
                .isNotNull();
        assertThat(ann.tenantLine()).isEqualTo("true");
    }

    @Test
    void inviteLookupAndClaimBypassTenantScoping() {
        assertIgnoresTenantLine(UserInviteMapper.class, "findActiveByTokenHash");
        assertIgnoresTenantLine(UserInviteMapper.class, "markUsed");
    }

    @Test
    void passwordResetLookupAndClaimBypassTenantScoping() {
        assertIgnoresTenantLine(PasswordResetTokenMapper.class, "findActiveByTokenHash");
        assertIgnoresTenantLine(PasswordResetTokenMapper.class, "markUsed");
    }

    /**
     * The bypass must stay narrow: only the token-keyed statements. Anything else on
     * these mappers is called from an authenticated request where scoping is correct
     * and desirable, so a blanket class-level ignore would be a real tenant hole.
     */
    @Test
    void theBypassIsPerMethod_notClassWide() {
        assertThat(UserInviteMapper.class.getAnnotation(InterceptorIgnore.class)).isNull();
        assertThat(PasswordResetTokenMapper.class.getAnnotation(InterceptorIgnore.class)).isNull();

        for (Class<?> mapper : List.of(UserInviteMapper.class, PasswordResetTokenMapper.class)) {
            List<String> ignored = java.util.Arrays.stream(mapper.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(InterceptorIgnore.class))
                    .map(Method::getName)
                    .sorted()
                    .toList();
            assertThat(ignored)
                    .as("%s: exactly the two token-keyed statements may bypass scoping",
                            mapper.getSimpleName())
                    .containsExactly("findActiveByTokenHash", "markUsed");
        }
    }
}
