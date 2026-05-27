package com.platform.core.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextTest {

    @AfterEach
    void clean() {
        RequestContext.clear();
    }

    @Test
    void tenantIdReturnsNullWhenUnset() {
        assertThat(RequestContext.tenantId()).isNull();
    }

    @Test
    void tenantIdOrDefaultFallsBackToDefault() {
        // No context set → fallback string. This is the contract every mapper
        // caller relies on when post-auth requests run outside an HTTP filter.
        assertThat(RequestContext.tenantIdOrDefault()).isEqualTo("demo");

        RequestContext.set("", null, null, Locale.JAPAN, "t1");
        assertThat(RequestContext.tenantIdOrDefault()).isEqualTo("demo");

        RequestContext.set("   ", null, null, Locale.JAPAN, "t1");
        assertThat(RequestContext.tenantIdOrDefault()).isEqualTo("demo");
    }

    @Test
    void tenantIdOrDefaultPassesThroughWhenSet() {
        RequestContext.set("acme", "user-1", "alice", Locale.JAPAN, "t1");
        assertThat(RequestContext.tenantIdOrDefault()).isEqualTo("acme");
        assertThat(RequestContext.tenantId()).isEqualTo("acme");
        assertThat(RequestContext.userId()).isEqualTo("user-1");
    }

    @Test
    void clearWipesEverything() {
        RequestContext.set("acme", "user-1", "alice", Locale.JAPAN, "t1");
        RequestContext.clear();
        assertThat(RequestContext.current()).isNull();
        assertThat(RequestContext.tenantId()).isNull();
    }
}
