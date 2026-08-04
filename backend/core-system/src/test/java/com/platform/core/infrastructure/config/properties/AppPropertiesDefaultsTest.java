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

        assertThat(p.rateLimit().enabled()).as("rate limiting on by default").isTrue();
        assertThat(p.lockout().enabled()).as("account lockout on by default").isTrue();
        assertThat(p.refreshCookie().secure()).as("refresh cookie Secure by default").isTrue();
        assertThat(p.refreshCookie().sameSite()).isEqualTo("Strict");
        assertThat(p.passwordPolicy().hibpEnabled()).as("breach check on by default").isTrue();
        assertThat(p.passwordPolicy().requireDigit()).isTrue();
        assertThat(p.passwordPolicy().requireUpper()).isTrue();
        assertThat(p.passwordPolicy().requireLower()).isTrue();
        assertThat(p.passwordPolicy().requireSymbol()).isTrue();
    }

    @Test
    void corsDefaultsToAllowingNothing() {
        // An empty origin list means no cross-origin caller is allowed — the safe
        // direction. WebMvcConfig only opens the wildcard path when it is asked to.
        assertThat(new AppCorsProperties(null).allowedOrigins()).isEmpty();
    }
}
