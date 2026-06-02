package com.platform.core.infrastructure.security.keycloak;

import com.platform.core.infrastructure.config.properties.AppKeycloakProperties;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bootstraps the backend's Keycloak provisioner client at startup.
 *
 * <p>The backend authenticates to Keycloak's Admin API as a dedicated
 * service-account client (see {@link KeycloakAdminClientFactory#runtimeClient()}).
 * That client has to exist before any tenant provisioning can happen. This
 * seeder creates it — using a one-time bootstrap admin credential
 * ({@link AppKeycloakProperties.Bootstrap}) — so neither a developer nor an
 * operator ever has to click around the Keycloak console:
 *
 * <ol>
 *   <li>Ensure the {@code access-matrix-provisioner} client exists in the admin
 *       realm (confidential, service accounts enabled), with its secret synced
 *       to config.</li>
 *   <li>Grant its service account the realm role {@code create-realm} so it can
 *       create new tenant realms (Keycloak auto-grants per-realm management on
 *       realms it then creates).</li>
 *   <li>Grant {@code manage-users}/{@code view-users} on each pre-existing
 *       managed realm (e.g. {@code demo}, {@code system}) — those were imported,
 *       NOT created by the provisioner, so the auto-grant doesn't cover them.</li>
 * </ol>
 *
 * <p>Runs at {@code HIGHEST_PRECEDENCE + 1} — before the KC user seeders
 * (+5 / +15) which use the runtime (provisioner) client and therefore need it
 * to exist and hold manage-users on demo/system first.
 *
 * <p>Idempotent and fail-fast: re-running only syncs; if Keycloak is
 * unreachable or the bootstrap credential is wrong, startup fails loudly
 * instead of pretending tenant provisioning will work. Disable entirely with
 * {@code app.keycloak.bootstrap.enabled=false} after the provisioner client has
 * been created and granted.
 */
@Component
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class KeycloakProvisionerSeeder {

    private static final Logger log = LoggerFactory.getLogger(KeycloakProvisionerSeeder.class);

    /** Realm role in the admin realm that allows creating new realms. */
    private static final String CREATE_REALM_ROLE = "create-realm";
    /** Client roles needed on each managed realm's {@code <realm>-realm} client. */
    private static final List<String> MANAGED_REALM_ROLES = List.of("manage-users", "view-users");
    /**
     * The throwaway provisioner secret shipped as the dev default in
     * application.yml. Detecting it at boot lets us scream if it ever reaches a
     * real deployment — a create-realm service account guarded by a
     * publicly-known secret is a serious hole. Keep in sync with
     * {@code app.keycloak.admin.client-secret}'s default.
     */
    private static final String DEV_DEFAULT_SECRET = "dev-provisioner-secret-change-in-prod";

    private final AppKeycloakProperties props;
    private final KeycloakAdminClientFactory factory;
    private final Environment env;

    public KeycloakProvisionerSeeder(AppKeycloakProperties props, KeycloakAdminClientFactory factory,
                                     Environment env) {
        this.props = props;
        this.factory = factory;
        this.env = env;
    }

    /** Result of {@link #ensureClient}: the client UUID and whether we just created it. */
    private record EnsureResult(String uuid, boolean created) {}

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public void seed() {
        AppKeycloakProperties.Bootstrap boot = props.bootstrap();
        if (!boot.enabled()) {
            log.info("[kc-provisioner] bootstrap disabled (app.keycloak.bootstrap.enabled=false) — skipping");
            return;
        }
        AppKeycloakProperties.Admin admin = props.admin();
        String provClientId = admin.clientId();
        String provSecret = admin.clientSecret();

        // Un-ignorable runtime flag: the dev-default secret must never reach a
        // real deployment. Fires on every boot that still uses it — harmless
        // noise in local dev, a loud banner otherwise. Multi-line on purpose so
        // it can't be skimmed past in startup logs.
        if (DEV_DEFAULT_SECRET.equals(provSecret)) {
            log.warn("""

                    ============================================================================
                    ⚠️  KEYCLOAK PROVISIONER — 用的是 DEV 默认 secret
                    ----------------------------------------------------------------------------
                      CORE_KEYCLOAK_PROVISIONER_SECRET 未设置，回退到公开已知的 dev 默认值
                      'dev-provisioner-secret-change-in-prod'。
                        • 本地开发：无所谓，忽略即可。
                        • 生产：绝不可用！这是一个 create-realm 服务账号的口令，
                          公开已知 = 严重安全漏洞。
                        • 生产请设强值： CORE_KEYCLOAK_PROVISIONER_SECRET=$(openssl rand -base64 32)
                    ============================================================================""");
        }

        try (Keycloak kc = factory.bootstrapClient()) {
            RealmResource master = kc.realm(admin.realm());

            EnsureResult ec = ensureClient(master, provClientId, provSecret);
            UserRepresentation saUser = master.clients().get(ec.uuid()).getServiceAccountUser();
            String saUserId = saUser.getId();

            grantRealmRole(master, saUserId, CREATE_REALM_ROLE);
            for (String realm : boot.managedRealms()) {
                grantManagedRealmRoles(master, saUserId, realm);
            }
            log.info("[kc-provisioner] provisioner ready (client='{}', granted create-realm + manage-users on {})",
                    provClientId, boot.managedRealms());
            warnIfBootstrapNotCleanedUp(ec);
        } catch (Exception e) {
            String msg = "Keycloak provisioner bootstrap failed for client '%s'. "
                    + "Check Keycloak is running and app.keycloak.bootstrap.* matches the Keycloak root admin "
                    + "(local default: admin/admin), then restart.";
            log.error(msg.formatted(provClientId), e);
            throw new IllegalStateException(msg.formatted(provClientId), e);
        }
    }

    /**
     * Create the provisioner client if missing; otherwise sync its secret and
     * confidential/service-account flags. Returns the client's internal UUID.
     */
    private EnsureResult ensureClient(RealmResource master, String clientId, String secret) {
        List<ClientRepresentation> existing = master.clients().findByClientId(clientId);
        if (existing != null && !existing.isEmpty()) {
            ClientRepresentation c = existing.get(0);
            // Keep config as the source of truth for the secret + key flags so a
            // rotated config secret takes effect on the next boot.
            c.setSecret(secret);
            c.setServiceAccountsEnabled(true);
            c.setStandardFlowEnabled(false);
            c.setDirectAccessGrantsEnabled(false);
            c.setPublicClient(false);
            master.clients().get(c.getId()).update(c);
            log.info("[kc-provisioner] client '{}' already present — secret + flags synced", clientId);
            return new EnsureResult(c.getId(), false);
        }
        ClientRepresentation c = new ClientRepresentation();
        c.setClientId(clientId);
        c.setName("access-matrix backend provisioner (service account)");
        c.setEnabled(true);
        c.setPublicClient(false);             // confidential
        c.setServiceAccountsEnabled(true);    // enables the client_credentials grant
        c.setStandardFlowEnabled(false);
        c.setDirectAccessGrantsEnabled(false);
        c.setSecret(secret);
        try (Response r = master.clients().create(c)) {
            if (r.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new IllegalStateException("create provisioner client failed: " + responseDetail(r));
            }
        }
        String uuid = master.clients().findByClientId(clientId).get(0).getId();
        log.info("[kc-provisioner] created provisioner client '{}' (uuid={})", clientId, uuid);
        return new EnsureResult(uuid, true);
    }

    /**
     * If bootstrap ran on a real (non-dev) environment AND the provisioner client
     * already existed, the one-time KC root credentials were never cleaned up after
     * the first boot. Scream — a standing create-everything root credential is a
     * needless risk, and bootstrap re-runs pointlessly on every restart.
     */
    private void warnIfBootstrapNotCleanedUp(EnsureResult ec) {
        if (ec.created() || isDevLikeProfile()) {
            return;   // first-ever creation, or a dev profile where re-running bootstrap is expected
        }
        log.warn("""

                ============================================================================
                ⚠️  KEYCLOAK BOOTSTRAP — 一次性引导凭据没清理
                ----------------------------------------------------------------------------
                  provisioner 客户端已存在（非首次创建），但 bootstrap 仍处于 enabled。
                  说明首次引导早已完成，却没撤掉一次性 KC root 凭据——它能管理整个 Keycloak，
                  留着 = 不必要的高权限风险，且每次重启都白连一次 KC 重跑引导。
                  请收紧：
                    • 设 CORE_KEYCLOAK_BOOTSTRAP_ENABLED=false
                    • 删除 CORE_KEYCLOAK_BOOTSTRAP_USERNAME / CORE_KEYCLOAK_BOOTSTRAP_PASSWORD
                ============================================================================""");
    }

    /** dev / local / test legitimately keep bootstrap enabled across boots — no nag there. */
    private boolean isDevLikeProfile() {
        for (String p : env.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(p) || "local".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    /** Grant a realm-level role to the service-account user (idempotent). */
    private void grantRealmRole(RealmResource master, String saUserId, String roleName) {
        RoleRepresentation role = master.roles().get(roleName).toRepresentation();
        master.users().get(saUserId).roles().realmLevel().add(List.of(role));
    }

    /**
     * Grant {@link #MANAGED_REALM_ROLES} on a pre-existing managed realm. In the
     * admin realm, each managed realm {@code X} is represented by a client named
     * {@code X-realm} that carries the admin roles (manage-users, view-users…).
     */
    private void grantManagedRealmRoles(RealmResource master, String saUserId, String managedRealm) {
        String realmClientId = managedRealm + "-realm";
        List<ClientRepresentation> hits = master.clients().findByClientId(realmClientId);
        if (hits == null || hits.isEmpty()) {
            log.warn("[kc-provisioner] no '{}' client in admin realm — is realm '{}' imported yet? "
                    + "skipping its grant (re-run bootstrap after it exists)", realmClientId, managedRealm);
            return;
        }
        String realmClientUuid = hits.get(0).getId();
        ClientResource realmClient = master.clients().get(realmClientUuid);
        List<RoleRepresentation> toAdd = new ArrayList<>();
        for (String roleName : MANAGED_REALM_ROLES) {
            toAdd.add(realmClient.roles().get(roleName).toRepresentation());
        }
        master.users().get(saUserId).roles().clientLevel(realmClientUuid).add(toAdd);
        log.info("[kc-provisioner] granted {} on managed realm '{}'", MANAGED_REALM_ROLES, managedRealm);
    }

    private String responseDetail(Response response) {
        if (response == null) return "HTTP <no response>";
        String body = "";
        try {
            if (response.hasEntity()) {
                body = response.readEntity(String.class);
            }
        } catch (Exception ignored) {
            body = "<unreadable response body>";
        }
        return body == null || body.isBlank()
                ? "HTTP " + response.getStatus()
                : "HTTP " + response.getStatus() + " body=" + body;
    }
}
