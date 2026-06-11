package com.platform.core.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the actuator-exposure fix: only the health endpoint may be public.
 * A bare {@code /actuator/**} permitAll leaks metrics / prometheus / caches /
 * info (route templates, cache structure, HikariCP + JVM internals) to
 * anonymous callers — incl. production, where the same endpoints are exposed.
 */
class SecurityConfigPermitPathsTest {

    private final List<String> permit = Arrays.asList(SecurityConfig.PERMIT_PATHS);

    @Test
    @DisplayName("no permit entry opens the whole actuator surface")
    void noBroadActuatorWildcard() {
        assertThat(permit)
                .as("only /actuator/health/** may be public; widening to /actuator/** "
                        + "re-introduces the unauthenticated metrics/prometheus info leak")
                .noneMatch(p -> p.equals("/actuator/**") || p.equals("/actuator")
                        || p.startsWith("/actuator/metrics") || p.startsWith("/actuator/prometheus")
                        || p.startsWith("/actuator/caches"));
    }

    @Test
    @DisplayName("health probes stay public")
    void healthIsPublic() {
        assertThat(permit).contains("/actuator/health/**");
    }
}
