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
        @DisplayName("*:* is PLATFORM super — matches platform: namespace only")
        void platformSuperWildcard() {
            // *:* is the PLATFORM_ADMIN's wildcard. It matches every
            // platform:* perm but explicitly does NOT match business
            // perms — privacy boundary (a platform admin shouldn't be
            // able to impersonate business users).
            Set<String> perms = Set.of("*:*");
            assertThat(PermissionMatcher.matches(perms, "platform:tenant:read")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "platform:tenant:create")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "platform:anything")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "user:read")).isFalse();
            assertThat(PermissionMatcher.matches(perms, "role:delete")).isFalse();
            assertThat(PermissionMatcher.matches(perms, "system:anything")).isFalse();
        }

        @Test
        @DisplayName("tenant:* is TENANT super — matches everything outside platform:")
        void tenantSuperWildcard() {
            // tenant:* is the business-tenant SUPER_ADMIN's wildcard.
            // Matches every business permission, but the platform:
            // namespace is carved out — a compromised business admin
            // cannot reach POST /platform/tenants.
            Set<String> perms = Set.of("tenant:*");
            assertThat(PermissionMatcher.matches(perms, "user:read")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "role:delete")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "auth:reset-password")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "anything:goes")).isTrue();
            assertThat(PermissionMatcher.matches(perms, "platform:tenant:read")).isFalse();
            assertThat(PermissionMatcher.matches(perms, "platform:tenant:create")).isFalse();
            assertThat(PermissionMatcher.matches(perms, "platform:anything")).isFalse();
        }

        @Test
        @DisplayName("the two supers don't shadow each other")
        void supersAreDisjoint() {
            // Critical invariant: holding one super wildcard never grants
            // the other namespace's perms. To get both authorities a user
            // would need both wildcards explicitly assigned.
            Set<String> platformOnly = Set.of("*:*");
            Set<String> tenantOnly = Set.of("tenant:*");
            Set<String> bothSupers = Set.of("*:*", "tenant:*");

            // platform-only: no business perms
            assertThat(PermissionMatcher.matches(platformOnly, "user:read")).isFalse();
            // tenant-only: no platform perms
            assertThat(PermissionMatcher.matches(tenantOnly, "platform:tenant:read")).isFalse();
            // both: god mode
            assertThat(PermissionMatcher.matches(bothSupers, "user:read")).isTrue();
            assertThat(PermissionMatcher.matches(bothSupers, "platform:tenant:read")).isTrue();
        }

        @Test
        @DisplayName("constants pin the wildcard literals")
        void constants() {
            assertThat(PermissionMatcher.SUPER).isEqualTo("*:*");
            assertThat(PermissionMatcher.TENANT_SUPER).isEqualTo("tenant:*");
            assertThat(PermissionMatcher.PLATFORM_NS).isEqualTo("platform:");
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
        @DisplayName("malformed required (no colon) — only tenant:* rescues it")
        void noColonRequired() {
            // Defensive: required permission strings should always be
            // resource:action. A bare "userread" is malformed; only the
            // catch-all wildcard tenant:* satisfies it (since it doesn't
            // start with "platform:"). *:* is platform-scoped now and does
            // NOT cover a colon-less value.
            assertThat(PermissionMatcher.matches(Set.of("tenant:*"), "userread")).isTrue();
            assertThat(PermissionMatcher.matches(Set.of("*:*"), "userread")).isFalse();
            assertThat(PermissionMatcher.matches(Set.of("user:*"), "userread")).isFalse();
            assertThat(PermissionMatcher.matches(Set.of("user:read"), "userread")).isFalse();
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
        void tenantSuperMatchesAnyBusinessRequest() {
            assertThat(PermissionMatcher.matchesAny(Set.of("tenant:*"), "user:read", "role:delete")).isTrue();
            assertThat(PermissionMatcher.matchesAny(Set.of("tenant:*"), "platform:tenant:read")).isFalse();
        }

        @Test
        void platformSuperMatchesAnyPlatformRequest() {
            assertThat(PermissionMatcher.matchesAny(Set.of("*:*"), "platform:tenant:read", "platform:tenant:create")).isTrue();
            assertThat(PermissionMatcher.matchesAny(Set.of("*:*"), "user:read")).isFalse();
        }
    }
}
