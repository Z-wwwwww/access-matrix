package com.platform.core.bootstrap.it;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every Keycloak realm fixture an IT imports must actually be on the test
 * classpath.
 *
 * <p>The ITs call {@code .withRealmImportFile("/x.json")}, and
 * testcontainers-keycloak resolves that against the <b>test classpath</b> — it is
 * not a repo path. Files get there through a {@code <testResource>} in
 * {@code core-bootstrap/pom.xml} that copies them out of
 * {@code infra/keycloak/realms/}. That include listed {@code default-realm.json},
 * a name that stopped existing when the realm was renamed to {@code demo}, so
 * Maven copied nothing:
 *
 * <pre>[INFO] Copying 0 resource from ..\..\infra\keycloak\realms to target\test-classes</pre>
 *
 * <p>Nothing caught it, because the ITs are Docker-gated
 * ({@code @Testcontainers(disabledWithoutDocker = true)}) and opt-in by design —
 * {@code backend/AGENTS.md} runs them with an explicit {@code -Dtest=...} — so a
 * machine without Docker skips green, and a machine with Docker boots an EMPTY
 * realm and fails looking like a Keycloak problem.
 *
 * <p>This test <b>scans the IT sources</b> rather than hardcoding one filename.
 * The first version of it pinned only the path {@code OidcJitProvisioningIT}
 * used, which is how {@code PasswordToSsoMigrationIT} was left still asking for
 * the long-gone {@code /default-realm.json} after the pom was repaired — fixing
 * one caller and declaring victory. Scanning means a third IT (or a rename on
 * either side) can't repeat that.
 *
 * <p>Deliberately a plain unit test, not an IT: it needs no Docker, so it runs in
 * every ordinary {@code mvn test}.
 */
class RealmImportFixtureTest {

    private static final Pattern IMPORT_CALL =
            Pattern.compile("withRealmImportFile\\(\\s*\"([^\"]+)\"");

    /** resource path → the IT source that asks for it. */
    private static Map<String, String> declaredImports() {
        Path itRoot = Path.of("src/test/java").toAbsolutePath().normalize();
        Map<String, String> out = new LinkedHashMap<>();
        try (var files = Files.walk(itRoot)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".java"))
                    // Skip this file: its own javadoc shows the call shape as an
                    // example, and the scanner would dutifully demand that the
                    // illustrative path exist.
                    .filter(p -> !p.getFileName().toString().equals("RealmImportFixtureTest.java"))
                    .toList()) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = IMPORT_CALL.matcher(src);
                while (m.find()) {
                    out.put(m.group(1), f.getFileName().toString());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot scan IT sources under " + itRoot, e);
        }
        return out;
    }

    @Test
    void everyRealmImportFileAnItAsksForIsOnTheTestClasspath() {
        Map<String, String> declared = declaredImports();

        assertThat(declared)
                .as("no withRealmImportFile(...) found — did the ITs move, or the scan break?")
                .isNotEmpty();

        for (Map.Entry<String, String> e : declared.entrySet()) {
            assertThat(getClass().getResource(e.getKey()))
                    .as("%s asks for %s, which is missing from the test classpath — check the "
                            + "<testResource> include in core-bootstrap/pom.xml against the actual "
                            + "file names in infra/keycloak/realms/", e.getValue(), e.getKey())
                    .isNotNull();
        }
    }

    @Test
    void theFixtureIsAKeycloakRealmExport() throws Exception {
        for (String resource : declaredImports().keySet()) {
            try (var in = getClass().getResourceAsStream(resource)) {
                assertThat(in).as("%s not readable", resource).isNotNull();
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                // Cheap sanity: really a realm export, not some other file that
                // happens to sit at that name.
                assertThat(json).as("%s does not look like a realm export", resource)
                        .contains("\"realm\"").contains("\"clients\"");
            }
        }
    }
}
