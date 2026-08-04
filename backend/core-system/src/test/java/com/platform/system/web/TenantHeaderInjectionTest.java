package com.platform.system.web;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tenant id that reaches {@code TenantLineInnerInterceptor} is spliced into the
 * SQL TEXT, not bound as a parameter — so it must never be raw request input.
 *
 * <p>{@code CoreRequestContextFilter} fills the tenant from the {@code X-Tenant-Id}
 * header on any PRE-AUTH request ({@code /auth/login}, {@code /auth/refresh},
 * {@code /invite/*}, {@code /reset-password/*}); authenticated requests take it from
 * the JWT {@code tid} claim instead. jsqlparser's {@link StringValue} does not escape:
 * its constructor parameter is literally named {@code escapedValue} and
 * {@code toString()} just wraps the value in quotes — verified directly, e.g.
 * {@code new StringValue("x' OR '1'='1").toString()} yields
 * {@code 'x' OR '1'='1'}.
 *
 * <p>These tests pin the two halves: that a hostile tenant string really does escape
 * its quotes in the rendered predicate, and that the filter's normalisation refuses
 * such a value so it can never get here.
 */
class TenantHeaderInjectionTest {

    private static TenantLineInnerInterceptor interceptorFor(String tenantId) {
        return new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new StringValue(tenantId);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return false;
            }
        });
    }

    private static String rewrite(String tenantId, String sql) {
        return interceptorFor(tenantId).parserSingle(sql, null);
    }

    @Test
    void aBenignTenantProducesTheExpectedScopedPredicate() {
        String out = rewrite("demo", "SELECT * FROM core_auth_user WHERE username = 'alice'");

        assertThat(out).contains("tenant_id = 'demo'");
    }

    /**
     * The headline: the value is spliced, so a quote in it terminates the literal and
     * the rest is parsed as SQL. On {@code /auth/login} that predicate turns
     * {@code findByIdentifier}'s tenant scoping into a tautology, i.e. a login attempt
     * nominally for a nonexistent tenant matches a user in ANY tenant.
     */
    @Test
    void aQuotedTenantEscapesTheLiteralAndDefeatsScoping() {
        String out = rewrite("x' OR '1'='1", "SELECT * FROM core_auth_user WHERE username = 'alice'");

        // Actual rendered statement (captured from this very interceptor):
        //   SELECT * FROM core_auth_user WHERE username = 'alice' AND tenant_id = 'x' OR '1'='1'
        // AND binds tighter than OR, so this reads
        //   (username = 'alice' AND tenant_id = 'x') OR ('1' = '1')
        // i.e. not merely a tenant-scope bypass — the predicate matches EVERY row.
        assertThat(out)
                .as("the tenant value is SQL text, not a bound parameter")
                .isEqualTo("SELECT * FROM core_auth_user WHERE username = 'alice' "
                        + "AND tenant_id = 'x' OR '1'='1'");
    }

    // ── the guard that must stop it ever reaching the interceptor ────────────

    @Test
    void normaliseRejectsInjectionAttempts() {
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("x' OR '1'='1")).isNull();
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("'")).isNull();
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("a b")).isNull();
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("UPPER")).isNull();
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("-leading-hyphen")).isNull();
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("a".repeat(64))).as("RFC1035 label caps at 63").isNull();
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant(null)).isNull();
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("   ")).isNull();
    }

    @Test
    void normaliseAcceptsRealTenantCodes() {
        // Same shape TenantDto.CreateRequest enforces on tenantCode, and the shape
        // Keycloak allows for a realm name (tenant_id == realm name is the convention).
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("demo")).isEqualTo("demo");
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant(" sozonext ")).isEqualTo("sozonext");
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("acme-inc")).isEqualTo("acme-inc");
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("system")).isEqualTo("system");
        assertThat(com.platform.core.infrastructure.web.CoreRequestContextFilter
                .normaliseTenant("a".repeat(63))).hasSize(63);
    }
}
