package com.platform.core.bootstrap.it;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Keycloak realm fixture that {@link OidcJitProvisioningIT} imports must
 * actually be on the test classpath.
 *
 * <p>That IT calls {@code .withRealmImportFile("/demo-realm.json")}, and
 * testcontainers-keycloak resolves that path against the <b>test classpath</b> —
 * it is not a repo path. The file gets there through a {@code <testResource>} in
 * {@code core-bootstrap/pom.xml} that copies it out of
 * {@code infra/keycloak/realms/}. That include listed {@code default-realm.json},
 * a name that stopped existing when the realm was renamed to {@code demo}
 * (the same rename V25 did for the tenant), so Maven copied nothing:
 *
 * <pre>[INFO] Copying 0 resource from ..\..\infra\keycloak\realms to target\test-classes</pre>
 *
 * <p>Nothing caught it. The IT is Docker-gated
 * ({@code @Testcontainers(disabledWithoutDocker = true)}) and is opt-in anyway —
 * {@code backend/AGENTS.md} documents running it explicitly with
 * {@code -Dtest='OidcJitProvisioningIT'} — so on a machine without Docker it
 * skips green, and on a machine WITH Docker it would boot an empty realm and
 * fail in a way that reads like a Keycloak problem rather than a build-wiring
 * one (the IT's own javadoc: "Without that copy the container boots an empty
 * realm").
 *
 * <p>This guard is deliberately a plain unit test, not an IT: it needs no Docker,
 * so it runs in every ordinary {@code mvn test} and fails immediately if the
 * copy is ever silently broken again by a rename on either side.
 */
class RealmImportFixtureTest {

    /** Must stay in sync with {@code OidcJitProvisioningIT.withRealmImportFile(...)}. */
    private static final String REALM_IMPORT_RESOURCE = "/demo-realm.json";

    @Test
    void realmImportFileIsOnTheTestClasspath() {
        assertThat(getClass().getResource(REALM_IMPORT_RESOURCE))
                .as("%s is missing from the test classpath — check the <testResource> "
                        + "include in core-bootstrap/pom.xml against the actual file names "
                        + "in infra/keycloak/realms/", REALM_IMPORT_RESOURCE)
                .isNotNull();
    }

    @Test
    void theFixtureIsTheRealmTheItImports() throws Exception {
        try (var in = getClass().getResourceAsStream(REALM_IMPORT_RESOURCE)) {
            assertThat(in).isNotNull();
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            // Cheap sanity: it really is the demo realm export the IT expects to
            // find a "demo" realm in, not some other file that happens to match.
            assertThat(json).contains("\"realm\"").contains("demo");
        }
    }
}
