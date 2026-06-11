package com.platform.system.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the frontend-dir resolution. The historic bug: relative
 * {@code ../frontend} was resolved against {@code user.dir} blindly and the
 * patcher {@code createDirectories}'d the result, so launching the app with
 * {@code user.dir = backend/core-bootstrap} (IDEA module run, spring-boot:run
 * fork) conjured a phantom {@code backend/frontend/} on every dev startup.
 * After the fix the patcher must:
 *   1. walk up from user.dir and accept a candidate only if its
 *      {@code package.json} exists
 *   2. skip patching entirely (no directory creation) when nothing matches
 */
class I18nPermissionPatcherTest {

    private static final Set<String> CODES = Set.of("user:read");

    private I18nPermissionPatcher patcher(String frontendDir) {
        I18nPermissionPatcher p = new I18nPermissionPatcher();
        ReflectionTestUtils.setField(p, "frontendDir", frontendDir);
        return p;
    }

    private void withUserDir(Path dir, ThrowingRunnable body) throws Exception {
        String old = System.getProperty("user.dir");
        System.setProperty("user.dir", dir.toString());
        try {
            body.run();
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable { void run() throws Exception; }

    @Test
    void resolvesRealFrontendByWalkingUpFromNestedModuleDir(@TempDir Path repo) throws Exception {
        Path frontend = Files.createDirectories(repo.resolve("frontend"));
        Files.writeString(frontend.resolve("package.json"), "{}");
        Path moduleDir = Files.createDirectories(repo.resolve("backend/core-bootstrap"));

        withUserDir(moduleDir, () -> patcher("../frontend").patch(CODES));

        assertThat(frontend.resolve("src/lang/generated/permissions.en.json")).exists();
        assertThat(repo.resolve("backend/frontend")).doesNotExist();
    }

    @Test
    void skipsAndCreatesNothingWhenNoFrontendExists(@TempDir Path repo) throws Exception {
        Path moduleDir = Files.createDirectories(repo.resolve("backend/core-bootstrap"));

        withUserDir(moduleDir, () -> patcher("../frontend").patch(CODES));

        assertThat(repo.resolve("backend/frontend")).doesNotExist();
        assertThat(repo.resolve("frontend")).doesNotExist();
    }

    @Test
    void trustsAbsoluteConfiguredPathEvenWithoutPackageJson(@TempDir Path repo) throws Exception {
        Path explicit = repo.resolve("elsewhere/frontend");

        withUserDir(repo, () -> patcher(explicit.toString()).patch(CODES));

        assertThat(explicit.resolve("src/lang/generated/permissions.ja_JP.json")).exists();
    }
}
