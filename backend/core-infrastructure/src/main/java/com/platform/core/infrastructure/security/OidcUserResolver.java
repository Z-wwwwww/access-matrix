package com.platform.core.infrastructure.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Resolves an incoming OIDC JWT to the business user id this request should
 * run under. The JWT's {@code sub} is the IdP's user UUID (Keycloak in our
 * case); the business primary key is a separate ULID — see V21 migration
 * and {@code core_auth_user.keycloak_id}.
 *
 * <p>Lives in {@code core-infrastructure} as an interface so the security
 * filter ({@code CoreRequestContextFilter}) does not need a compile-time
 * dependency on {@code core-system} (where the business user table lives).
 * The {@code core-system} module provides the only implementation,
 * {@code OidcJitUserService}, which performs Just-In-Time provisioning.
 *
 * <p>When no implementation is on the classpath (legacy / password-only
 * deployments), {@code CoreRequestContextFilter} falls back to using the
 * JWT subject as-is.
 */
public interface OidcUserResolver {

    /**
     * Map an OIDC JWT to a business user id within the JWT's tenant.
     *
     * <p>Side effect: may insert into {@code core_auth_user} (Just-In-Time
     * provisioning) on the very first call for a given Keycloak identity,
     * and may UPDATE the {@code keycloak_id} column on an existing
     * legacy-password row when it sees the same username for the first
     * time over OIDC. Subsequent calls hit the indexed
     * {@code (tenant_id, keycloak_id)} lookup and are read-only.
     *
     * @return the business {@code core_auth_user.id} (ULID) to install into
     *         {@code RequestContext} for this request, or {@code null} if
     *         the JWT can't be mapped to any business user and provisioning
     *         is not permitted (e.g. JIT disabled for this tenant).
     */
    String resolveBusinessUserId(Jwt jwt);
}
