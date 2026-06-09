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
 * Single owner of the session / Keycloak side-effects of changing a user's
 * access (disable / enable / delete / force-logout / password-reset). Both the
 * business-user console ({@code UserAdminService}) and the platform-user console
 * ({@code PlatformUserAdminService}) route through here so the two never drift
 * apart again — they previously hand-rolled this and the business side silently
 * missed the Keycloak sync (a disabled user could still SSO back in).
 *
 * <p>Two distinct gates back each other up:
 * <ul>
 *   <li><b>App kick</b> ({@link ForceLogoutService}) — already-issued access
 *       tokens are rejected by {@code ForceLogoutFilter} on the next request.</li>
 *   <li><b>Keycloak</b> — disabling the KC user makes Keycloak itself refuse the
 *       login, and ending the KC session stops a kicked user from being silently
 *       re-authenticated on the {@code /login} redirect. A DB {@code status=0}
 *       alone does NOT stop SSO (the OIDC JIT resolver doesn't check status).</li>
 * </ul>
 *
 * <p>The Keycloak step is best-effort: a KC hiccup must not block the local kick
 * (the stronger, immediate gate). KC is only present in oidc mode.
 */
@Service
public class SessionTerminationService {

    private static final Logger log = LoggerFactory.getLogger(SessionTerminationService.class);

    private final ForceLogoutService forceLogout;
    private final ObjectProvider<KeycloakUserService> kcProvider;
    /** Raw lookup of a user's (tenant_id, keycloak_id) — deliberately cross-tenant
        (bypasses the MyBatis tenant interceptor) and ignores mark so a being-deleted
        user is still acted on in Keycloak. */
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
     * silently re-authenticated. Use where access is revoked but the account's
     * enabled flag is unchanged: delete / force-logout / password-reset.
     */
    public void terminateUser(String userId) {
        if (userId == null || userId.isBlank()) return;
        forceLogout.kickOut(userId);
        withKcUser(userId, KeycloakUserService::logoutUser);
    }

    /**
     * Apply a user's enabled/disabled state everywhere it must take effect:
     * <ul>
     *   <li>disable → kick in-flight tokens, disable the KC user (KC refuses the
     *       login) and end the live KC session;</li>
     *   <li>enable → clear the kick and re-enable the KC user.</li>
     * </ul>
     * Callers ({@code UserAdminService} / {@code PlatformUserAdminService}) own the
     * DB {@code status} write + their domain guards; the side-effects live here.
     */
    public void applyEnabled(String userId, boolean enabled) {
        if (userId == null || userId.isBlank()) return;
        if (enabled) {
            forceLogout.clear(userId);
        } else {
            forceLogout.kickOut(userId);
        }
        withKcUser(userId, (kc, tenant, kcId) -> {
            kc.setEnabled(tenant, kcId, enabled);
            if (!enabled) kc.logoutUser(tenant, kcId);   // end the live SSO session
        });
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

    /** A Keycloak action against a resolved (tenant, keycloakId). */
    @FunctionalInterface
    private interface KcUserAction {
        void run(KeycloakUserService kc, String tenant, String keycloakId);
    }

    /** Resolve the user's (tenant, keycloakId) once and run {@code action} (best-effort). */
    private void withKcUser(String userId, KcUserAction action) {
        KeycloakUserService kc = kcProvider.getIfAvailable();
        if (kc == null) return;   // non-oidc mode: nothing to do
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT tenant_id, keycloak_id FROM core_auth_user WHERE id = ?", userId);
            String kcId = (String) row.get("keycloak_id");
            String tenant = (String) row.get("tenant_id");
            if (kcId != null && !kcId.isBlank()) {
                action.run(kc, tenant, kcId);
            }
        } catch (EmptyResultDataAccessException e) {
            // user row gone (hard-deleted) — nothing to do
        } catch (RuntimeException e) {
            // best-effort: the local kick/clear already stands; log for visibility
            log.warn("[session] Keycloak side-effect failed for user {} (local state still applied): {}",
                    userId, e.toString());
        }
    }
}
