package com.platform.system.auth.service;

import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * One place to fully terminate sessions, so a kick can't be silently undone by a
 * still-live Keycloak SSO session.
 *
 * <p>The bare {@link ForceLogoutService#kickOut} only invalidates already-issued
 * access tokens (the {@code ForceLogoutFilter} rejects tokens with {@code iat <=}
 * the kick). It does NOT end the user's Keycloak session — so a kicked user who
 * hits {@code /login} → "Sign in with SSO" is silently re-authenticated by the
 * live KC session and gets a fresh token straight back in. Ending the KC session
 * here forces a real re-authentication (and, if the account/tenant is disabled,
 * the login then fails for the right reason). Mirrors what
 * {@code PlatformUserAdminService} already does for platform-ops users.
 *
 * <p>The KC step is best-effort: a Keycloak hiccup must not block the local kick
 * (which is the stronger, immediate gate). KC is only present in oidc mode.
 */
@Service
public class SessionTerminationService {

    private static final Logger log = LoggerFactory.getLogger(SessionTerminationService.class);

    private final ForceLogoutService forceLogout;
    private final ObjectProvider<KeycloakUserService> kcProvider;
    /** Raw lookup of a user's (tenant_id, keycloak_id) — deliberately cross-tenant
        (bypasses the MyBatis tenant interceptor) and ignores mark so a being-deleted
        user is still logged out of Keycloak. */
    private final JdbcTemplate jdbc;

    public SessionTerminationService(ForceLogoutService forceLogout,
                                     ObjectProvider<KeycloakUserService> kcProvider,
                                     JdbcTemplate jdbc) {
        this.forceLogout = forceLogout;
        this.kcProvider = kcProvider;
        this.jdbc = jdbc;
    }

    /**
     * Kick the user's tokens AND end their Keycloak SSO session, so they cannot be
     * silently re-authenticated. Use everywhere a user is disabled / deleted /
     * force-logged-out / password-reset.
     */
    public void terminateUser(String userId) {
        if (userId == null || userId.isBlank()) return;
        forceLogout.kickOut(userId);
        endKeycloakSession(userId);
    }

    /** Force-log-out every user of a tenant (tenant suspended). KC realm is disabled
        separately, so an SSO re-login into the suspended realm is already blocked. */
    public void terminateTenant(String tenantCode) {
        forceLogout.kickOutTenant(tenantCode);
    }

    /** Clear a tenant-wide kick (tenant resumed). */
    public void reactivateTenant(String tenantCode) {
        forceLogout.clearTenant(tenantCode);
    }

    /** Clear a per-user kick (user re-enabled), so fresh tokens aren't rejected. */
    public void reactivateUser(String userId) {
        forceLogout.clear(userId);
    }

    private void endKeycloakSession(String userId) {
        KeycloakUserService kc = kcProvider.getIfAvailable();
        if (kc == null) return;   // non-oidc mode: nothing to end
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT tenant_id, keycloak_id FROM core_auth_user WHERE id = ?", userId);
            String kcId = (String) row.get("keycloak_id");
            String tenantId = (String) row.get("tenant_id");
            if (kcId != null && !kcId.isBlank()) {
                kc.logoutUser(tenantId, kcId);
            }
        } catch (EmptyResultDataAccessException e) {
            // user row gone (hard-deleted) — nothing to do
        } catch (RuntimeException e) {
            // best-effort: the local kick already stands; log for visibility
            log.warn("[session] Keycloak logout failed for user {} (local kick still in effect): {}",
                    userId, e.toString());
        }
    }
}
