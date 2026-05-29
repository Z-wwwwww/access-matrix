package com.platform.core.infrastructure.security.keycloak;

import com.platform.core.infrastructure.config.properties.AppKeycloakProperties;
import jakarta.ws.rs.WebApplicationException;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Thin facade over Keycloak's Admin REST realm-level operations: create,
 * disable, lookup. Used by {@code TenantAdminService} to provision new
 * business tenants and soft-disable retired ones.
 *
 * <h3>Why a separate service from KeycloakUserService</h3>
 * KeycloakUserService operates inside an existing realm (user CRUD).
 * Realm-level operations sit one layer above — creating a realm, looking
 * it up, disabling it. Keeping them apart matches the natural authority
 * split (realm management = PLATFORM_ADMIN, user management within a
 * realm = realm's SUPER_ADMIN or PLATFORM_ADMIN via cross-tenant).
 *
 * <h3>Realm-creation strategy</h3>
 * To avoid copy-pasting a 2700-line realm JSON into Java code, this
 * service loads the {@code demo-realm.json} template from the
 * classpath (it lives next to the migration SQL since it's effectively
 * application data), surgically mutates three fields, and POSTs the
 * resulting JSON to Keycloak's Admin API:
 *
 * <ol>
 *   <li>{@code realm}: "demo" → new tenant code</li>
 *   <li>{@code displayName}: "Demo Tenant" → caller-supplied display name</li>
 *   <li>The {@code tid} hardcoded-claim-mapper's {@code claim.value}:
 *       "demo" → new tenant code (this is what makes the JWT carry
 *       the right tenant id)</li>
 * </ol>
 *
 * Everything else (client config, default scopes, theme, login flow)
 * carries over from the template — exactly the same surface area
 * {@code infra/keycloak/new-tenant.{ps1,sh}} produces, just driven from
 * the running backend instead of a shell script.
 *
 * <p>Only active when {@code app.security.mode=oidc} — the legacy
 * password mode has no realms.
 */
@Service
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class KeycloakRealmService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRealmService.class);

    /**
     * Classpath path to the realm template. The demo realm is the canonical
     * shape; new business tenants are clones of it with three fields swapped.
     * The file ships in core-infrastructure resources, copied from
     * infra/keycloak/realms/demo-realm.json at build time (see pom).
     */
    private static final String TEMPLATE_PATH = "keycloak/demo-realm.json";

    private final AppKeycloakProperties.Admin cfg;
    private final JsonMapper jsonMapper;

    public KeycloakRealmService(AppKeycloakProperties props, JsonMapper jsonMapper) {
        this.cfg = props.admin();
        this.jsonMapper = jsonMapper;
    }

    /**
     * Create a new Keycloak realm by cloning the demo template and
     * retargeting the three identifier fields. Returns silently on
     * success; throws {@link KeycloakUserService.KeycloakOperationException}
     * on any KC-side failure (network, authz, conflict).
     *
     * @param tenantCode lowercase RFC1035 label; becomes both the realm
     *                   name AND the {@code tid} claim value on every
     *                   token the realm issues
     * @param displayName human-readable name shown on login pages and
     *                    in the KC admin console realm picker
     */
    public void createRealmFromTemplate(String tenantCode, String displayName) {
        validateTenantCode(tenantCode);
        String body = renderRealmJson(tenantCode, displayName);
        // The Java client's kc.realms().create() takes a RealmRepresentation
        // POJO; round-tripping our String JSON through Jackson + KC's
        // Jackson would double-parse and risk losing fields the POJO
        // doesn't model (e.g. nested protocolMapper configs). Easier to
        // POST the raw body and let Keycloak parse it.
        try (Keycloak kc = newAdminClient()) {
            // The admin client's underlying ResteasyClient is preconfigured
            // with the bearer token. Importing via the realms resource accepts
            // a RealmRepresentation, so we deserialize our mutated JSON into
            // one via the client's own Jackson — Keycloak's library bundles
            // Jackson 2 internally and tolerates the same JSON shape it
            // produced.
            RealmRepresentation rep = decodeRealmRepresentation(body);
            kc.realms().create(rep);
            log.info("[kc-realm] created realm '{}' (displayName='{}')", tenantCode, displayName);
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 409) {
                throw new KeycloakUserService.KeycloakOperationException(
                        "Realm '" + tenantCode + "' already exists in Keycloak");
            }
            throw new KeycloakUserService.KeycloakOperationException(
                    "Keycloak create-realm failed: HTTP " + status, e);
        }
    }

    /**
     * Disable a realm — sets enabled=false so KC refuses to issue tokens
     * but does NOT remove the realm or its users. Reversible via the
     * admin console.
     *
     * <p>Used as the KC-side of soft-deleting a tenant: business data
     * stays, KC users still exist (for audit), but nobody can sign in.
     */
    public void disableRealm(String tenantCode) {
        validateTenantCode(tenantCode);
        try (Keycloak kc = newAdminClient()) {
            RealmResource rr = kc.realm(tenantCode);
            RealmRepresentation rep = rr.toRepresentation();
            if (rep == null) {
                throw new KeycloakUserService.KeycloakOperationException(
                        "Realm '" + tenantCode + "' not found in Keycloak");
            }
            rep.setEnabled(false);
            rr.update(rep);
            log.info("[kc-realm] disabled realm '{}'", tenantCode);
        } catch (NotFoundException e) {
            throw new KeycloakUserService.KeycloakOperationException(
                    "Realm '" + tenantCode + "' not found in Keycloak", e);
        } catch (WebApplicationException e) {
            throw new KeycloakUserService.KeycloakOperationException(
                    "Keycloak disable-realm failed: HTTP " + e.getResponse().getStatus(), e);
        }
    }

    /**
     * Re-enable a realm — the symmetric counterpart of {@link #disableRealm}
     * for the suspend / resume tenant workflow. Sets enabled=true so KC
     * resumes issuing tokens for users in this realm.
     *
     * <p>No-op on a realm that's already enabled (KC's PUT replaces the
     * full representation; updating to the same state is harmless).
     */
    public void enableRealm(String tenantCode) {
        validateTenantCode(tenantCode);
        try (Keycloak kc = newAdminClient()) {
            RealmResource rr = kc.realm(tenantCode);
            RealmRepresentation rep = rr.toRepresentation();
            if (rep == null) {
                throw new KeycloakUserService.KeycloakOperationException(
                        "Realm '" + tenantCode + "' not found in Keycloak");
            }
            rep.setEnabled(true);
            rr.update(rep);
            log.info("[kc-realm] enabled realm '{}'", tenantCode);
        } catch (NotFoundException e) {
            throw new KeycloakUserService.KeycloakOperationException(
                    "Realm '" + tenantCode + "' not found in Keycloak", e);
        } catch (WebApplicationException e) {
            throw new KeycloakUserService.KeycloakOperationException(
                    "Keycloak enable-realm failed: HTTP " + e.getResponse().getStatus(), e);
        }
    }

    /**
     * Update the realm's displayName attribute — what the KC admin
     * console shows above the realm picker. Called by the platform
     * tenant-edit flow so KC and core_tenant don't drift.
     *
     * <p>Realm name itself ({@code tenant_code}) stays immutable: renaming
     * it would invalidate every JWT's {@code iss} claim and break sessions.
     */
    public void updateDisplayName(String tenantCode, String displayName) {
        validateTenantCode(tenantCode);
        try (Keycloak kc = newAdminClient()) {
            RealmResource rr = kc.realm(tenantCode);
            RealmRepresentation rep = rr.toRepresentation();
            if (rep == null) {
                throw new KeycloakUserService.KeycloakOperationException(
                        "Realm '" + tenantCode + "' not found in Keycloak");
            }
            rep.setDisplayName(displayName);
            rr.update(rep);
            log.info("[kc-realm] updated displayName for realm '{}'", tenantCode);
        } catch (NotFoundException e) {
            throw new KeycloakUserService.KeycloakOperationException(
                    "Realm '" + tenantCode + "' not found in Keycloak", e);
        } catch (WebApplicationException e) {
            throw new KeycloakUserService.KeycloakOperationException(
                    "Keycloak update-realm failed: HTTP " + e.getResponse().getStatus(), e);
        }
    }

    /**
     * Physically delete a realm — drops realm config, users, sessions,
     * clients, audit events. Irreversible. Called by the hard-delete
     * tenant flow AFTER all per-tenant DB rows have been deleted.
     *
     * <p>Ordering note: the orchestrating service runs DB deletes BEFORE
     * this KC delete. If the DB-side succeeds and this KC call fails,
     * we end up with an orphan realm in Keycloak — the operator can
     * delete it manually from the admin console. The reverse (KC delete
     * succeeds, DB delete fails) would leave business data unreachable
     * but still consuming space — worse, because the recovery path
     * involves manually recreating the realm. Hence DB-first.
     */
    public void deleteRealm(String tenantCode) {
        validateTenantCode(tenantCode);
        try (Keycloak kc = newAdminClient()) {
            kc.realm(tenantCode).remove();
            log.info("[kc-realm] deleted realm '{}'", tenantCode);
        } catch (NotFoundException e) {
            // Already gone — treat as success so a retry of a half-failed
            // hard delete completes cleanly rather than tripping here.
            log.warn("[kc-realm] realm '{}' was already absent during delete", tenantCode);
        } catch (WebApplicationException e) {
            throw new KeycloakUserService.KeycloakOperationException(
                    "Keycloak delete-realm failed: HTTP " + e.getResponse().getStatus(), e);
        }
    }

    /**
     * {@code true} if a realm with this exact code exists in Keycloak.
     * Used by {@code TenantAdminService.create} to fail-fast before
     * touching the DB.
     */
    public boolean realmExists(String tenantCode) {
        validateTenantCode(tenantCode);
        try (Keycloak kc = newAdminClient()) {
            kc.realm(tenantCode).toRepresentation();
            return true;
        } catch (NotFoundException e) {
            return false;
        } catch (Exception e) {
            log.warn("[kc-realm] lookup '{}' failed ({}), treating as absent", tenantCode, e.toString());
            return false;
        }
    }

    // ─── internals ────────────────────────────────────────────────────

    private static void validateTenantCode(String s) {
        if (s == null || !s.matches("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")) {
            throw new IllegalArgumentException(
                    "tenant code must be a lowercase RFC1035 label, was: " + s);
        }
    }

    /**
     * Load the demo realm template and apply the three surgical replaces.
     * Same logic as infra/keycloak/new-tenant.{ps1,sh} but in JVM-land —
     * blanket s/demo/code/ would damage unrelated keys like
     * {@code default-roles-*}, so targeted regex replaces only.
     */
    private String renderRealmJson(String tenantCode, String displayName) {
        String template;
        try {
            template = new String(
                    new ClassPathResource(TEMPLATE_PATH).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Realm template missing on classpath: " + TEMPLATE_PATH
                            + " — check core-infrastructure/pom.xml resources block",
                    e);
        }
        // The replacements MUST be precise — the JSON contains many
        // "demo" / "Demo Tenant" strings in unrelated contexts (e.g. a
        // permission's name field). We target only the realm-name field,
        // the displayName field, and the tid mapper claim.value.
        String out = template
                .replaceFirst("\"realm\"\\s*:\\s*\"demo\"",
                        "\"realm\": " + quote(tenantCode))
                .replaceFirst("\"displayName\"\\s*:\\s*\"Demo Tenant\"",
                        "\"displayName\": " + quote(displayName))
                .replaceFirst("\"claim\\.value\"\\s*:\\s*\"demo\"",
                        "\"claim.value\": " + quote(tenantCode));
        return out;
    }

    private static String quote(String s) {
        // Minimal JSON string escaping — tenant code is RFC1035 (no
        // special chars) and displayName is operator-supplied but kept
        // safe by validation upstream. Still defensive on quote / backslash.
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private RealmRepresentation decodeRealmRepresentation(String json) {
        // We use our Jackson 3 mapper to parse to a Map first, then
        // hand-shape into RealmRepresentation via its setters. Direct
        // Jackson 3 → KC's Jackson 2-annotated POJO would mostly work
        // for top-level fields but quietly drop nested ones (clients,
        // protocolMappers etc.). Use the KC client's own Jackson 2
        // pathway by re-serializing via java util.
        //
        // Pragmatic shortcut: bridge through the KC client's bundled
        // Jackson 2 via the kc admin client's JsonSerialization helper.
        // It accepts an InputStream of JSON and returns a fully-shaped
        // RealmRepresentation. The KC team ships this exact helper for
        // realm imports, so we're using it the way it was intended.
        //
        // Strict parse (fail-on-unknown) is intentional: keycloak-admin-client
        // is pinned to MATCH the server version (see backend/pom.xml), so the
        // template's fields are all modelled and any unknown field signals a
        // real problem — a typo in the template or a forgotten client/server
        // version bump — that we want to surface loudly rather than silently
        // drop. If you upgrade the Keycloak server, bump the client to match.
        try {
            return org.keycloak.util.JsonSerialization.readValue(
                    new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                    RealmRepresentation.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse realm template JSON", e);
        }
    }

    private Keycloak newAdminClient() {
        KeycloakBuilder b = KeycloakBuilder.builder()
                .serverUrl(cfg.serverUrl())
                .realm(cfg.realm())
                .clientId(cfg.clientId());
        if (cfg.isServiceAccount()) {
            b.grantType(OAuth2Constants.CLIENT_CREDENTIALS)
             .clientSecret(cfg.clientSecret());
        } else {
            b.grantType(OAuth2Constants.PASSWORD)
             .username(cfg.username())
             .password(cfg.password());
        }
        return b.build();
    }
}
