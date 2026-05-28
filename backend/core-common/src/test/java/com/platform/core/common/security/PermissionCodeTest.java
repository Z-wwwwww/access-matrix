package com.platform.core.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link PermissionCode#register} format rules, particularly:
 * <ul>
 *   <li>2-segment codes ({@code resource:action}) — original convention</li>
 *   <li>3+ segment codes ({@code platform:tenant:read}) — namespace nesting,
 *       added when the platform-ops namespace landed. Regression here means
 *       <em>the app fails to boot</em> (SystemPermissions / PlatformPermissions
 *       static blocks throw on class init), so this test is the canary.</li>
 * </ul>
 * Also covers {@link PermissionRegistry#put}'s last-colon split rule which
 * keeps DB {@code resource} / {@code action} columns in sync with V28's seed.
 */
class PermissionCodeTest {

    @Nested
    @DisplayName("register(): format validation")
    class Format {

        @Test
        @DisplayName("two-segment lowercase code is accepted")
        void twoSegment() {
            String code = "test-module:test-action-" + System.nanoTime();
            PermissionCode.register(code, "test");
            assertThat(PermissionRegistry.isRegistered(code)).isTrue();
        }

        @Test
        @DisplayName("three-segment namespace code (platform:tenant:read style) is accepted")
        void threeSegment() {
            // Why this matters: PLATFORM_ADMIN permissions live in the platform:
            // namespace and use 3 segments. If this regex regresses, every class
            // that calls registerAll() on a Permissions-style constants holder
            // explodes at class-init time and the whole Spring context fails
            // to start. Catch it here, not in a 30-second boot.
            String code = "platform:thing-" + System.nanoTime() + ":read";
            PermissionCode.register(code, "platform");
            assertThat(PermissionRegistry.isRegistered(code)).isTrue();
        }

        @Test
        @DisplayName("four-segment is also accepted (no arbitrary depth cap)")
        void fourSegment() {
            String code = "a:b:c:d-" + System.nanoTime();
            PermissionCode.register(code, "test");
            assertThat(PermissionRegistry.isRegistered(code)).isTrue();
        }

        @Test
        @DisplayName("single segment (no colon) is rejected")
        void singleSegment() {
            assertThatThrownBy(() -> PermissionCode.register("nocolon", "test"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid permission code format");
        }

        @Test
        @DisplayName("uppercase letters are rejected")
        void uppercaseRejected() {
            assertThatThrownBy(() -> PermissionCode.register("User:Read", "test"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("empty segments (leading/trailing/double colon) are rejected")
        void emptySegmentRejected() {
            assertThatThrownBy(() -> PermissionCode.register(":read", "test"))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PermissionCode.register("user:", "test"))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PermissionCode.register("user::read", "test"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("null / blank is rejected")
        void nullRejected() {
            assertThatThrownBy(() -> PermissionCode.register(null, "test"))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PermissionCode.register("", "test"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("PermissionRegistry split: resource/action via LAST colon")
    class RegistrySplit {

        @Test
        @DisplayName("two-segment: first colon == last colon, behaves as before")
        void twoSegmentSplit() {
            String code = "user-x:read-" + System.nanoTime();
            PermissionCode.register(code, "system");
            PermissionRegistry.Entry e = PermissionRegistry.get(code);
            assertThat(e.resource()).startsWith("user-x");
            assertThat(e.action()).startsWith("read");
        }

        @Test
        @DisplayName("three-segment: resource keeps the namespace prefix, action is the last segment")
        void threeSegmentSplit() {
            // V28's INSERT was: resource='platform:tenant', action='read'.
            // If we ever flip to first-colon split, PermissionConsistencyGuard's
            // upsert would rewrite the DB columns to resource='platform',
            // action='tenant:read' on every boot. Lock the contract.
            String suffix = String.valueOf(System.nanoTime());
            String code = "platform:tenant-" + suffix + ":read";
            PermissionCode.register(code, "platform");
            PermissionRegistry.Entry e = PermissionRegistry.get(code);
            assertThat(e.resource()).isEqualTo("platform:tenant-" + suffix);
            assertThat(e.action()).isEqualTo("read");
            assertThat(e.module()).isEqualTo("platform");
        }
    }
}
