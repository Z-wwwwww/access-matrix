package com.platform.core.bootstrap.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scaffolds a new business resource (entity / mapper / service / controller / dto)
 * + a Flyway migration, by cloning {@code business-demo/task/*} with token
 * substitution. Run from the repo root via Maven exec:
 *
 * <pre>{@code
 * ./mvnw -pl core-bootstrap exec:java \
 *     -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \
 *     -Dexec.args="order"
 * }</pre>
 *
 * <p>The generated code lives under {@code business-demo/}'s package tree
 * (a sandbox for new business resources during exploration). Promoting a
 * resource into its own Maven module is a separate, manual step — this
 * tool intentionally doesn't edit {@code backend/pom.xml} or create new
 * {@code business-*} modules in v1, to keep the blast radius small.
 *
 * <p>What you get for {@code scaffold order}:
 * <ul>
 *   <li>{@code OrderEntity.java}, {@code OrderMapper.java},
 *       {@code OrderService.java}, {@code OrderController.java},
 *       {@code OrderDto.java} — same shape as {@code TaskEntity} et al,
 *       with identifiers and table names substituted</li>
 *   <li>{@code V<N>__create_business_order.sql} — minimal CREATE TABLE
 *       with the required {@code tenant_id} + audit columns + a single
 *       {@code code} sample column and a tenant-prefixed unique index;
 *       version number auto-picked to be the next free V in the module's
 *       migration directory</li>
 * </ul>
 *
 * <p>Conventions inherited from the template:
 * <ul>
 *   <li>entity extends {@code BaseEntity} (tenant_id + audit auto-filled)</li>
 *   <li>mapper carries {@code @DataScope(deptColumn = "dept_id", creatorColumn = "create_user")}
 *       — adjust if your entity has different scope columns</li>
 *   <li>controller endpoints carry {@code @RequiresPermission} referencing
 *       constants from {@code DemoPermissions} that don't exist yet —
 *       you must add them as a follow-up step (see printed next-steps)</li>
 * </ul>
 *
 * <p>Refuses to overwrite an existing resource directory. To regenerate,
 * delete the directory first.
 */
public final class BusinessModuleScaffold {

    private BusinessModuleScaffold() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args[0].equals("--help") || args[0].equals("-h")) {
            usage();
            return;
        }
        String resource = args[0].toLowerCase();
        if (!resource.matches("[a-z][a-z0-9]*")) {
            die("resource must match [a-z][a-z0-9]* — was: " + args[0]);
        }
        String resourceCap = capitalize(resource);
        String resourceUpper = resource.toUpperCase();

        Path repo = findRepoRoot();
        Path moduleJava = repo.resolve("backend/business-demo/src/main/java/com/platform/business/demo");
        // Flyway scans only core-bootstrap's db/migration in the current
        // setup. Business migrations go there too, using V1000+ versions to
        // avoid colliding with framework V1-V999. Multi-location Flyway
        // (one dir per business module) is a future config change.
        Path moduleMigrations = repo.resolve("backend/core-bootstrap/src/main/resources/db/migration");
        Path templateDir = moduleJava.resolve("task");
        Path newResourceDir = moduleJava.resolve(resource);

        if (!Files.isDirectory(templateDir)) {
            die("template not found: " + templateDir + " — is business-demo/task still present?");
        }
        if (Files.exists(newResourceDir)) {
            die("resource already exists: " + newResourceDir + " — delete it to regenerate");
        }

        int version = pickNextFlywayVersion(moduleMigrations);

        Substitutions sub = new Substitutions(resource, resourceCap, resourceUpper);

        // Clone the 5 Java files with token substitution.
        List<String> created = new ArrayList<>();
        for (Path src : listJavaFilesRecursive(templateDir)) {
            Path rel = templateDir.relativize(src);
            // entity/TaskEntity.java → entity/<Resource>Entity.java
            String newFilename = rel.getFileName().toString().replace("Task", resourceCap);
            Path dest = newResourceDir.resolve(rel.getParent() == null ? Paths.get("") : rel.getParent()).resolve(newFilename);
            Files.createDirectories(dest.getParent());
            String content = Files.readString(src, StandardCharsets.UTF_8);
            Files.writeString(dest, sub.apply(content), StandardCharsets.UTF_8);
            created.add(repo.relativize(dest).toString().replace('\\', '/'));
        }

        // Generate the migration from an inline template (NOT cloned from
        // V10__demo_task.sql — that file carries role / menu / permission
        // seeds we don't want to duplicate for every new resource).
        Path migration = moduleMigrations.resolve("V" + version + "__create_business_" + resource + ".sql");
        Files.writeString(migration, renderMigration(resource, version), StandardCharsets.UTF_8);
        created.add(repo.relativize(migration).toString().replace('\\', '/'));

        printSuccess(resource, resourceCap, resourceUpper, version, created);
    }

    // ─── token substitution ──────────────────────────────────────────

    private record Substitutions(String lower, String cap, String upper) {
        String apply(String src) {
            // Order matters: replace longer / more specific tokens first to
            // avoid catastrophic substring overlaps. e.g. "demo_task" must
            // map to "business_<resource>" BEFORE we replace bare "task".
            String out = src;
            out = out.replace("demo_task", "business_" + lower);
            // Java identifier: "Task" → "<Cap>" before "task" → "<lower>".
            out = replaceWordBoundary(out, "Task", cap);
            out = replaceWordBoundary(out, "TASK_", upper + "_");
            out = replaceWordBoundary(out, "TASK", upper);
            out = replaceWordBoundary(out, "task", lower);
            return out;
        }
    }

    /**
     * Replace {@code needle} with {@code repl} only when surrounded by
     * non-identifier characters (so {@code task} replaces in
     * {@code task.entity} but not in {@code tasks} or {@code datastructure}).
     */
    private static String replaceWordBoundary(String src, String needle, String repl) {
        Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(needle) + "(?![A-Za-z0-9_])");
        Matcher m = p.matcher(src);
        return m.replaceAll(Matcher.quoteReplacement(repl));
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private static Path findRepoRoot() {
        // exec:java's cwd is the module dir by default (-pl core-bootstrap →
        // backend/core-bootstrap). Walk up until we see infra/ + frontend/
        // siblings, our reliable repo-root markers.
        Path cwd = Paths.get("").toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("infra")) && Files.isDirectory(p.resolve("frontend"))
                    && Files.isDirectory(p.resolve("backend"))) {
                return p;
            }
        }
        die("could not find repo root from cwd=" + cwd);
        return null;
    }

    private static List<Path> listJavaFilesRecursive(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        Files.walk(dir).filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        return out;
    }

    private static int pickNextFlywayVersion(Path migrations) throws IOException {
        int max = 0;
        if (Files.isDirectory(migrations)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(migrations, "V*.sql")) {
                Pattern verPat = Pattern.compile("^V(\\d+)__.*\\.sql$");
                for (Path p : stream) {
                    Matcher m = verPat.matcher(p.getFileName().toString());
                    if (m.matches()) {
                        int n = Integer.parseInt(m.group(1));
                        if (n > max) max = n;
                    }
                }
            }
        }
        // Convention: business-* migrations start at V1000.
        return Math.max(max + 1, 1000);
    }

    private static String renderMigration(String resource, int version) {
        String table = "business_" + resource;
        return "-- V" + version + "__create_" + table + ".sql\n"
                + "-- Auto-generated by BusinessModuleScaffold.\n"
                + "-- TODO: replace the placeholder business columns + add seeds as needed.\n"
                + "\n"
                + "CREATE TABLE " + table + " (\n"
                + "    id            CHAR(26)     NOT NULL PRIMARY KEY,\n"
                + "    tenant_id     VARCHAR(64)  NOT NULL,                   -- required; TenantSchemaGuard fail-fasts otherwise\n"
                + "    -- ---- business columns (placeholder — edit me) ----\n"
                + "    code          VARCHAR(64),\n"
                + "    name          VARCHAR(128),\n"
                + "    -- ---- audit (auto-filled by AuditMetaObjectHandler + BaseEntity) ----\n"
                + "    mark          SMALLINT     NOT NULL DEFAULT 1,\n"
                + "    create_user   VARCHAR(64),\n"
                + "    update_user   VARCHAR(64),\n"
                + "    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                + "    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP\n"
                + ");\n"
                + "\n"
                + "-- Unique indexes lead with tenant_id so two tenants can each have the same 'code':\n"
                + "CREATE UNIQUE INDEX uk_" + resource + "_code\n"
                + "    ON " + table + " (tenant_id, code) WHERE mark = 1;\n";
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void usage() {
        System.out.println("""
                Usage:
                  ./mvnw -pl core-bootstrap exec:java \\
                      -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \\
                      -Dexec.args="<resource>"

                Example:
                  -Dexec.args="order"

                Generates: business-demo/<resource>/{entity,mapper,service,controller,dto}/*.java
                           business-demo/src/main/resources/db/migration/V<N>__create_business_<resource>.sql

                Resource name must match [a-z][a-z0-9]* (e.g. order, invoice, salesreport).
                """);
    }

    private static void printSuccess(String resource, String cap, String upper, int version, List<String> files) {
        System.out.println();
        System.out.println("✓ Scaffolded resource '" + resource + "' (" + cap + ") at V" + version);
        System.out.println("──────────────────────────────────────────────────────────────");
        System.out.println("Created:");
        for (String f : files) System.out.println("  " + f);
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Edit V" + version + "__create_business_" + resource
                + ".sql — replace placeholder columns with your business shape");
        System.out.println("  2. Edit " + cap + "Entity.java + " + cap + "Dto.java to match");
        System.out.println("  3. Add 4 permission constants to DemoPermissions.java:");
        System.out.println("       " + upper + "_READ   = \"" + resource + ":read\"");
        System.out.println("       " + upper + "_CREATE = \"" + resource + ":create\"");
        System.out.println("       " + upper + "_UPDATE = \"" + resource + ":update\"");
        System.out.println("       " + upper + "_DELETE = \"" + resource + ":delete\"");
        System.out.println("     (the controller already references these — won't compile until added)");
        System.out.println("  4. Implement TODO bodies in " + cap + "Service.java");
        System.out.println("  5. ./mvnw test       — ArchitectureTest will verify conventions");
        System.out.println("  6. ./mvnw spring-boot:run  — TenantSchemaGuard verifies the migration");
        System.out.println();
        System.out.println("Reference: backend/business-demo/task/ (the template you just cloned)");
        System.out.println();
    }

    private static void die(String msg) {
        System.err.println("ERROR: " + msg);
        System.exit(1);
    }
}
