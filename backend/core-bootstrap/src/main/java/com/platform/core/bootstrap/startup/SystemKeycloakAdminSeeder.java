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
 * Keycloak-side counterpart of {@link SystemAdminSeeder}: ensures the
 * 'system' realm has an 'ops' user with a permanent password matching
 * the DB hash, so the same {@code ops / ops} credential works on both
 * the password and SSO paths during local dev.
 *
 * <p>Conditional on:
 * <ul>
 *   <li>{@code local} profile — the platform-ops realm in prod is
 *       provisioned out-of-band, not by an application startup hook.</li>
 *   <li>{@code app.security.mode=oidc} — without OIDC the Keycloak admin
 *       client beans aren't even loaded; this seeder would NPE.</li>
 * </ul>
 *
 * <p>Idempotent — only creates the user when missing. Won't reset a
 * password the dev may have rotated.
 *
 * <p>Order = {@code HIGHEST_PRECEDENCE + 15}, AFTER
 * {@link LocalKeycloakAdminSeeder} ({@code +5}) and {@link SystemAdminSeeder}
 * ({@code +10}). The DB user must exist before we sync to KC so the JIT
 * bind path (which keys off {@code (tenant, username)}) can link them on
 * first SSO login.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class SystemKeycloakAdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(SystemKeycloakAdminSeeder.class);

    private static final String SYSTEM_REALM   = "system";
    private static final String OPS_USERNAME   = "ops";
    private static final String OPS_EMAIL      = "ops@platform.local";
    private static final String OPS_DISPLAY    = "Platform Ops";
    /** Dev-default password — must match SystemAdminSeeder.OPS_PASSWORD. */
    private static final String OPS_PASSWORD   = "ops";

    private final KeycloakUserService keycloakUserService;

    public SystemKeycloakAdminSeeder(KeycloakUserService keycloakUserService) {
        this.keycloakUserService = keycloakUserService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 15)
    public void seed() {
        try {
            if (keycloakUserService.userExists(SYSTEM_REALM, OPS_USERNAME)) {
                log.info("SystemKeycloakAdminSeeder: '{}' already present in realm '{}' — skipping",
                        OPS_USERNAME, SYSTEM_REALM);
                return;
            }
            String kcId = keycloakUserService.createUser(
                    SYSTEM_REALM, OPS_USERNAME, OPS_EMAIL, OPS_DISPLAY, OPS_PASSWORD);
            keycloakUserService.setPassword(SYSTEM_REALM, kcId, OPS_PASSWORD, /* temporary= */ false);
            log.info("SystemKeycloakAdminSeeder: provisioned '{}' (kcId={}) in realm '{}'",
                    OPS_USERNAME, kcId, SYSTEM_REALM);
        } catch (Exception e) {
            // Same fail-soft posture as LocalKeycloakAdminSeeder — startup
            // shouldn't crash just because Keycloak isn't reachable yet.
            log.warn("SystemKeycloakAdminSeeder: could not ensure '{}' in Keycloak ({}). "
                            + "Start Keycloak then restart the app, OR create the user manually "
                            + "in the 'system' realm.",
                    OPS_USERNAME, e.toString());
        }
    }
}
