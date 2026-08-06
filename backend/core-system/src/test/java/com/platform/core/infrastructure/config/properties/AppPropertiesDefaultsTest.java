package com.platform.core.infrastructure.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the direction every security-relevant fallback in this package must fail.
 *
 * <p>These records' compact constructors are what runs when the corresponding
 * config subtree is absent from the effective configuration (a reorganised
 * {@code application.yml}, an external {@code spring.config.location} that omits
 * the block, a new profile). For anything that gates a boundary the fallback has
 * to be the SAFE value, because a missing key produces no error and no log.
 *
 * <p>The tenant flag is the one that actually regressed: it defaulted to
 * {@code false}, i.e. absent config ⇒ the MyBatis tenant-line interceptor is never
 * installed ⇒ no {@code WHERE tenant_id = ?} anywhere ⇒ silent cross-tenant reads.
 * {@code TenantSchemaGuard} could not catch it either — it skips itself when the
 * same flag is false. Meanwhile application.yml and .env.example both state the
 * default is ON ("绝不默认跨租户可见").
 */
class AppPropertiesDefaultsTest {

    @Test
    void tenantIsolationDefaultsToEnabled_whenTheWholeSubtreeIsMissing() {
        AppMybatisProperties p = new AppMybatisProperties(null);

        assertThat(p.tenant()).isNotNull();
        assertThat(p.tenant().enabled())
                .as("absent app.mybatis config must still isolate tenants — a false default "
                        + "silently drops row-level isolation and TenantSchemaGuard skips too")
                .isTrue();
    }

    @Test
    void securityFallbacksAllPickTheSafeValue() {
        AppSecurityProperties p = new AppSecurityProperties(null, null, null, null, null, null);

        // The single most consequential field in this record, and the one the test
        // did not cover. SecurityConfig branches on it: "permit-all" wires
        // `anyRequest().permitAll()` — no authentication anywhere. application.yml
        // promises the opposite for exactly this case: 「base 默认 oidc（fail-closed：
        // 即使某 profile 漏配 mode，也默认带鉴权而非放行）」. A blank APP_SECURITY_MODE
        // (an env var the prod comment tells operators to use) is enough to reach it.
        assertThat(p.mode())
                .as("a missing/blank mode must land on an AUTHENTICATED mode, never permit-all")
                .isNotEqualTo("permit-all");
        assertThat(p.mode()).isEqualTo("oidc");

        assertThat(p.rateLimit().enabled()).as("rate limiting on by default").isTrue();
        assertThat(p.lockout().enabled()).as("account lockout on by default").isTrue();
        assertThat(p.refreshCookie().secure()).as("refresh cookie Secure by default").isTrue();
        assertThat(p.refreshCookie().sameSite()).isEqualTo("Strict");
        assertThat(p.passwordPolicy().hibpEnabled()).as("breach check on by default").isTrue();
        assertThat(p.passwordPolicy().requireDigit()).isTrue();
        assertThat(p.passwordPolicy().requireUpper()).isTrue();
        assertThat(p.passwordPolicy().requireLower()).isTrue();
        assertThat(p.passwordPolicy().requireSymbol()).isTrue();
        // The one policy flag whose SAFE value is false, and the only one this test
        // did not pin — every other assertion above happens to be safe-when-true.
        // application.yml documents `false = fail-closed(本默认)`: when HIBP is
        // unreachable, refuse the password change rather than let a breached password
        // through. The record's fallback passed `true`, so a deployment that omits
        // app.security.password-policy silently got the opposite posture, which is the
        // same shape as the AppMybatisProperties `new Tenant(false)` fallback.
        assertThat(p.passwordPolicy().failOpenOnHibpError())
                .as("HIBP unreachable must fail CLOSED by default — matching the "
                        + "documented application.yml value, not the reverse")
                .isFalse();
    }

    @Test
    void corsDefaultsToAllowingNothing() {
        // An empty origin list means no cross-origin caller is allowed — the safe
        // direction. WebMvcConfig only opens the wildcard path when it is asked to.
        assertThat(new AppCorsProperties(null).allowedOrigins()).isEmpty();
    }
}
