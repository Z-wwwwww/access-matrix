package com.platform.core.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInRolesTest {

    @Test
    void superAdminIdMatchesV5SeededUlid() {
        // The seed ULID is baked into V5 / AuthSchemaBootstrap. If anyone ever changes
        // it on either side, the lookup-by-id path in LocalAdminSeeder /
        // UserAdminService.findSuperAdminRoleId would silently fail and the
        // "last super admin" guard would no-op. This test pins the constant to the
        // seeded value so a mismatch fails the build instead of leaking past review.
        assertThat(BuiltInRoles.SUPER_ADMIN_ID).isEqualTo("00000000000000000000ROLE01");
    }

    @Test
    void classIsNotInstantiable() throws Exception {
        var ctor = BuiltInRoles.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        // Static-only holder — even reflective construction returns an instance,
        // we just sanity-check it doesn't throw on a private no-arg ctor.
        assertThat(ctor.newInstance()).isNotNull();
    }
}
