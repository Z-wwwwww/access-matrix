package com.platform.core.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionMatcherTest {

    @Nested
    @DisplayName("matches(): exact / wildcard semantics")
    class Matches {

        @Test
        @DisplayName("exact match")
        void exact() {
            assertThat(PermissionMatcher.matches(Set.of("user:read"), "user:read")).isTrue();
            assertThat(PermissionMatcher.matches(Set.of("user:read"), "user:delete")).isFalse();
            assertThat(PermissionMatcher.matches(Set.of("user:read"), "role:read")).isFalse();
        }

        @Test
        @DisplayName("*:* grants every business-tenant permission")
        void superWildcard() {
            Set<String> perms = Set.of("*:*");
            assertThat(PermissionMatcher.matches(perms, "user:read")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "role:delete")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "system:anything")).isTrue();
            // NB: "platform:*" is the exception — see platformCarveOut below.
        }

        @Test
        @DisplayName("*:* deliberately does NOT match platform:* — privilege carve-out")
        void platformCarveOut() {
            // Business-tenant SUPER_ADMIN holds *:*. They should NOT thereby
            // satisfy platform-ops permissions — PLATFORM_ADMIN is a
            // distinct scope, and *:*'s blanket grant must stop at the
            // platform: namespace boundary. Without this carve-out a
            // compromised SUPER_ADMIN could reach POST /platform/tenants
            // and create realms.
            Set<String> businessSuper = Set.of("*:*");
            assertThat(PermissionMatcher.matches(businessSuper, "platform:tenant:read"))
                    .as("*:* must not satisfy platform:tenant:read")
                    .isFalse();
            assertThat(PermissionMatcher.matches(businessSuper, "platform:tenant:create"))
                    .isFalse();
            assertThat(PermissionMatcher.matches(businessSuper, "platform:tenant:delete"))
                    .isFalse();
            assertThat(PermissionMatcher.matches(businessSuper, "platform:anything"))
                    .isFalse();

            // platform:* DOES satisfy platform:tenant:* (via the
            // resource:* branch).
            Set<String> platformAdmin = Set.of("platform:*");
            assertThat(PermissionMatcher.matches(platformAdmin, "platform:tenant:read")).isTrue();
            assertThat(PermissionMatcher.matches(platformAdmin, "platform:tenant:create")).isTrue();
            assertThat(PermissionMatcher.matches(platformAdmin, "platform:anything")).isTrue();
            // And platform:* must NOT shadow business-tenant perms either
            // — symmetry. A PLATFORM_ADMIN cannot impersonate a SUPER_ADMIN.
            assertThat(PermissionMatcher.matches(platformAdmin, "user:read")).isFalse();
            assertThat(PermissionMatcher.matches(platformAdmin, "role:delete")).isFalse();

            // Exact platform code also works (no wildcard at all).
            Set<String> readOnly = Set.of("platform:tenant:read");
            assertThat(PermissionMatcher.matches(readOnly, "platform:tenant:read")).isTrue();
            assertThat(PermissionMatcher.matches(readOnly, "platform:tenant:create")).isFalse();
        }

        @Test
        @DisplayName("resource:* grants every action on that resource only")
        void resourceWildcard() {
            Set<String> perms = Set.of("user:*");
            assertThat(PermissionMatcher.matches(perms, "user:read")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "user:delete")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "role:read")).isFalse();
        }

        @Test
        @DisplayName("null / empty / blank — fail-closed")
        void nullSafety() {
            assertThat(PermissionMatcher.matches(null, "user:read")).isFalse();
            assertThat(PermissionMatcher.matches(Set.of(), "user:read")).isFalse();
            assertThat(PermissionMatcher.matches(Set.of("user:read"), null)).isFalse();
            assertThat(PermissionMatcher.matches(Set.of("user:read"), "")).isFalse();
            assertThat(PermissionMatcher.matches(Set.of("user:read"), "   ")).isFalse();
        }

        @Test
        @DisplayName("malformed required (no colon) — never matches via wildcard")
        void noColonRequired() {
            // Defensive: required permission strings should always be resource:action.
            // If a caller passes a malformed value, we don't fall back to "match by prefix".
            assertThat(PermissionMatcher.matches(Set.of("*:*"), "userread")).isTrue();  // *:* covers all
            assertThat(PermissionMatcher.matches(Set.of("user:*"), "userread")).isFalse();
            assertThat(PermissionMatcher.matches(Set.of("user:read"), "userread")).isFalse();
        }

        @Test
        @DisplayName("SUPER constant equals literal *:*")
        void superConstant() {
            assertThat(PermissionMatcher.SUPER).isEqualTo("*:*");
        }
    }

    @Nested
    @DisplayName("matchesAny(): OR-semantics across required permissions")
    class MatchesAny {

        @Test
        void anyOne() {
            Set<String> userPerms = Set.of("user:read");
            assertThat(PermissionMatcher.matchesAny(userPerms, "user:read", "role:read")).isTrue();
            assertThat(PermissionMatcher.matchesAny(userPerms, "role:read", "user:read")).isTrue();
            assertThat(PermissionMatcher.matchesAny(userPerms, "role:read", "menu:read")).isFalse();
        }

        @Test
        void nullAndEmpty() {
            assertThat(PermissionMatcher.matchesAny(Set.of("user:read"), (String[]) null)).isFalse();
            assertThat(PermissionMatcher.matchesAny(Set.of("user:read"))).isFalse();
        }

        @Test
        void superAdminMatchesAnyRequest() {
            assertThat(PermissionMatcher.matchesAny(Set.of("*:*"), "user:read", "role:delete")).isTrue();
        }
    }
}
