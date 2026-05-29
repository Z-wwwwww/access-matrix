package com.platform.core.infrastructure.security.keycloak;

import com.platform.core.infrastructure.config.properties.AppKeycloakProperties;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

/**
 * Thin facade over Keycloak's Admin REST client for the operations the
 * business code actually needs: create / set password / disable / delete.
 *
 * <p>Only active when {@code app.security.mode=oidc} — the legacy
 * {@code AdminAuthController.login} path doesn't need any of this, and
 * loading the Keycloak admin client in that mode would just waste a JAR
 * load + a TCP probe at boot.
 *
 * <p>Realm: every call takes a realm parameter (== business tenant id).
 * Each tenant lives in its own Keycloak realm; cross-tenant management
 * goes through separate Admin client connections.
 */
@Service
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class KeycloakUserService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserService.class);

    private final KeycloakAdminClientFactory adminClients;

    public KeycloakUserService(KeycloakAdminClientFactory adminClients) {
        this.adminClients = adminClients;
    }

    /**
     * Provision a Keycloak user.
     *
     * @param realm        target realm name (= business tenant id)
     * @param username     Keycloak username
     * @param email        user's email; required for invite mode
     * @param displayName  full display name (split into first/last by Keycloak)
     * @param tempPassword nullable. When non-null + non-blank, the user is
     *                     created with this as a TEMPORARY password (Keycloak
     *                     forces them to change it on first login). When
     *                     null/blank, the user is created with no credentials
     *                     — caller must follow up with {@link #setPassword}
     *                     (e.g. via the invite-link landing page).
     * @return Keycloak's UUID for the new user
     */
    public String createUser(String realm,
                             String username,
                             String email,
                             String displayName,
                             String tempPassword) {
        UserRepresentation u = new UserRepresentation();
        u.setUsername(username);
        u.setEmail(email);
        u.setEnabled(true);
        u.setEmailVerified(true);   // admin-created users skip email verification
        if (displayName != null && !displayName.isBlank()) {
            int sp = displayName.indexOf(' ');
            if (sp > 0) {
                u.setFirstName(displayName.substring(0, sp));
                u.setLastName(displayName.substring(sp + 1));
            } else {
                u.setFirstName(displayName);
            }
        }
        if (tempPassword != null && !tempPassword.isBlank()) {
            CredentialRepresentation c = new CredentialRepresentation();
            c.setType(CredentialRepresentation.PASSWORD);
            c.setValue(tempPassword);
            c.setTemporary(true);
            u.setCredentials(List.of(c));
        }

        try (Keycloak kc = newAdminClient();
             Response r = kc.realm(realm).users().create(u)) {
            int status = r.getStatus();
            if (status == Response.Status.CREATED.getStatusCode()) {
                URI loc = r.getLocation();
                String kcId = loc == null ? null : loc.getPath().substring(loc.getPath().lastIndexOf('/') + 1);
                log.info("[kc] created user '{}' in realm '{}' (id={})", username, realm, kcId);
                return kcId;
            }
            if (status == Response.Status.CONFLICT.getStatusCode()) {
                throw new KeycloakOperationException("Keycloak user already exists: " + username);
            }
            throw new KeycloakOperationException("Keycloak create-user failed: HTTP " + status);
        }
    }

    /**
     * Set / reset a user's password. {@code temporary=false} means it sticks
     * (used after the invite landing page collects a permanent password).
     */
    public void setPassword(String realm, String keycloakUserId, String newPassword, boolean temporary) {
        try (Keycloak kc = newAdminClient()) {
            UserResource ur = kc.realm(realm).users().get(keycloakUserId);
            CredentialRepresentation c = new CredentialRepresentation();
            c.setType(CredentialRepresentation.PASSWORD);
            c.setValue(newPassword);
            c.setTemporary(temporary);
            ur.resetPassword(c);
            log.info("[kc] set password (temporary={}) for user {} in realm {}", temporary, keycloakUserId, realm);
        } catch (WebApplicationException e) {
            throw new KeycloakOperationException("Keycloak set-password failed: HTTP " + e.getResponse().getStatus(), e);
        }
    }

    /**
     * Trigger one of Keycloak's "required actions" by email — e.g.
     * {@code UPDATE_PASSWORD}, {@code VERIFY_EMAIL}, {@code CONFIGURE_TOTP}.
     *
     * <p>Used by the password→SSO migration runner: after mirroring a
     * legacy DB user into Keycloak with NO credentials, this fires off the
     * "set your password" email so the user can complete enrollment without
     * any admin intervention. The recipient hits an OIDC reset-credentials
     * page; Keycloak verifies the link, prompts for a new password against
     * the realm's password policy, and stores the argon2id hash on its side.
     *
     * <p>Requires the realm's SMTP settings to be configured and the user
     * row to have a {@code email}. KC will throw HTTP 500 if either is missing.
     */
    public void executeActionsEmail(String realm,
                                    String keycloakUserId,
                                    List<String> actions) {
        try (Keycloak kc = newAdminClient()) {
            kc.realm(realm).users().get(keycloakUserId).executeActionsEmail(actions);
            log.info("[kc] executeActionsEmail({}) for user {} in realm {}",
                    actions, keycloakUserId, realm);
        } catch (WebApplicationException e) {
            throw new KeycloakOperationException(
                    "Keycloak executeActionsEmail failed: HTTP " + e.getResponse().getStatus(), e);
        }
    }

    /**
     * Find the Keycloak UUID for a given username inside a realm. Returns null
     * if no exact-match user exists. Useful for the migration runner so we can
     * skip users that already live in Keycloak (e.g. partial earlier mirror run).
     */
    public String findUserIdByUsername(String realm, String username) {
        try (Keycloak kc = newAdminClient()) {
            List<UserRepresentation> hits = kc.realm(realm).users()
                    .searchByUsername(username, /* exact = */ true);
            if (hits == null || hits.isEmpty()) return null;
            return hits.get(0).getId();
        }
    }

    public void disableUser(String realm, String keycloakUserId) {
        try (Keycloak kc = newAdminClient()) {
            UserResource ur = kc.realm(realm).users().get(keycloakUserId);
            UserRepresentation u = ur.toRepresentation();
            u.setEnabled(false);
            ur.update(u);
            log.info("[kc] disabled user {} in realm {}", keycloakUserId, realm);
        } catch (WebApplicationException e) {
            throw new KeycloakOperationException("Keycloak disable-user failed: HTTP " + e.getResponse().getStatus(), e);
        }
    }

    /**
     * Returns {@code true} iff a user with this exact username exists in the
     * realm (case-sensitive). Used by bootstrap seeders to ensure a default
     * admin exists without double-creating on every restart.
     */
    public boolean userExists(String realm, String username) {
        try (Keycloak kc = newAdminClient()) {
            List<UserRepresentation> hits = kc.realm(realm).users().searchByUsername(username, true);
            return hits != null && !hits.isEmpty();
        }
    }

    public void deleteUser(String realm, String keycloakUserId) {
        try (Keycloak kc = newAdminClient();
             Response r = kc.realm(realm).users().delete(keycloakUserId)) {
            int s = r.getStatus();
            if (s != Response.Status.NO_CONTENT.getStatusCode()
                    && s != Response.Status.NOT_FOUND.getStatusCode()) {
                throw new KeycloakOperationException("Keycloak delete-user failed: HTTP " + s);
            }
            log.info("[kc] deleted user {} in realm {} (HTTP {})", keycloakUserId, realm, s);
        }
    }

    /** Runtime admin client — single path (provisioner, client_credentials). */
    private Keycloak newAdminClient() {
        return adminClients.runtimeClient();
    }

    /** Unchecked wrapper for Keycloak Admin REST failures. */
    public static class KeycloakOperationException extends RuntimeException {
        public KeycloakOperationException(String message) { super(message); }
        public KeycloakOperationException(String message, Throwable cause) { super(message, cause); }
    }

    /** Co-locate the @ConfigurationProperties registration with its consumer. */
    @Configuration
    @EnableConfigurationProperties(AppKeycloakProperties.class)
    static class Bindings {}
}
