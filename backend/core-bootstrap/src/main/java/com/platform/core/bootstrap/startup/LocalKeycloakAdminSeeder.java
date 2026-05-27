package com.platform.core.bootstrap.startup;

import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures Keycloak's {@code default} realm always has an {@code admin} user
 * with password {@code admin} on local-profile boot. Pairs with
 * {@link LocalAdminSeeder}, which guarantees the corresponding business
 * {@code core_auth_user} row + SUPER_ADMIN binding exist on the application
 * side.
 *
 * <p>Why both seeders: the two systems own different halves of the identity:
 * <ul>
 *   <li>Keycloak owns: username + password + MFA + sessions.</li>
 *   <li>core_auth_user + RBAC tables own: business attributes, roles,
 *       department, data scope.</li>
 * </ul>
 *
 * <p>First-time SSO login as {@code admin} works like this:
 * <pre>
 *   1. User picks "Sign in with SSO" → Keycloak login page.
 *   2. Submits admin / admin (this seeder ensured the Keycloak user exists).
 *   3. Keycloak returns to backend with a JWT, sub = Keycloak UUID.
 *   4. OidcJitUserService.resolveBusinessUserId:
 *      - looks up by (tenant=default, keycloak_id=&lt;UUID&gt;) → miss
 *      - looks up by (tenant=default, username=admin)          → HIT
 *        (LocalAdminSeeder seeded this row with SUPER_ADMIN + HQ dept)
 *      - writes keycloak_id onto that row → bind path complete
 *      - returns its ULID
 *   5. RequestContext.userId = that ULID → admin has full perms immediately.
 * </pre>
 *
 * <p>Dev only (@Profile local + mode=oidc). Prod uses a properly-managed
 * IdP — bootstrap admins there come from infra/IT processes, not from
 * application startup hooks.
 *
 * <p>Idempotent: only creates the user when missing. Never resets the
 * password on subsequent boots, so a dev rotating the local admin
 * password isn't undone by the next restart.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class LocalKeycloakAdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(LocalKeycloakAdminSeeder.class);

    private static final String TENANT_REALM   = "demo";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_EMAIL    = "admin@platform.local";
    private static final String ADMIN_DISPLAY  = "Local Admin";
    /**
     * Dev-default password — intentionally aligned with LocalAdminSeeder's
     * local password ("admin") so the same credential works whether the
     * backend is in {@code mode=password} or {@code mode=oidc}. Rotate this
     * in any environment a person other than you can reach.
     */
    private static final String ADMIN_PASSWORD = "admin";

    private final KeycloakUserService keycloakUserService;

    public LocalKeycloakAdminSeeder(KeycloakUserService keycloakUserService) {
        this.keycloakUserService = keycloakUserService;
    }

    /**
     * Order = HIGHEST_PRECEDENCE + 5 so this runs AFTER {@link LocalAdminSeeder}
     * (HIGHEST_PRECEDENCE). The order matters in two ways:
     *   - by the time a Keycloak admin user can possibly log in, the
     *     business core_auth_user row + SUPER_ADMIN binding already exist;
     *   - if the Keycloak Admin REST call fails (server still booting,
     *     network blip), the business seeding already succeeded — we don't
     *     want the IdP-side hiccup to block the rest of startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    public void seed() {
        try {
            if (keycloakUserService.userExists(TENANT_REALM, ADMIN_USERNAME)) {
                log.info("LocalKeycloakAdminSeeder: '{}' already present in realm '{}' — skipping",
                        ADMIN_USERNAME, TENANT_REALM);
                return;
            }
            // createUser sets the password as TEMPORARY (first-login change forced).
            // We immediately override with a PERMANENT password so the dev default
            // doesn't trip a password-change required-action on every fresh setup.
            String kcId = keycloakUserService.createUser(
                    TENANT_REALM, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_DISPLAY, ADMIN_PASSWORD);
            keycloakUserService.setPassword(TENANT_REALM, kcId, ADMIN_PASSWORD, /* temporary= */ false);
            log.info("LocalKeycloakAdminSeeder: provisioned '{}' (kcId={}) in realm '{}'",
                    ADMIN_USERNAME, kcId, TENANT_REALM);
        } catch (Exception e) {
            // Don't crash startup — the app is still useful (other flows /
            // health endpoints work) and a dev can recover by either:
            //   (a) starting Keycloak and restarting the app, OR
            //   (b) manually creating 'admin' in the Keycloak admin console.
            log.warn("LocalKeycloakAdminSeeder: could not ensure '{}' in Keycloak ({}). "
                    + "Start Keycloak then restart the app, OR create the user manually.",
                    ADMIN_USERNAME, e.toString());
        }
    }
}
