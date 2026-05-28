package com.platform.core.bootstrap.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scaffolds a new business resource (entity / mapper / service / controller / dto)
 * + a Flyway migration, with optional fresh-module creation.
 *
 * <h3>Modes</h3>
 *
 * <h4>Legacy mode (no flag) — scaffold into business-demo</h4>
 * <pre>{@code
 * ./mvnw -pl core-bootstrap exec:java \
 *     -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \
 *     -Dexec.args="invoice"
 * }</pre>
 * Generates 5 Java files under {@code backend/business-demo/.../invoice/} +
 * a migration under {@code backend/core-bootstrap/.../db/migration/}, and
 * <b>auto-injects 4 permission constants into {@code DemoPermissions.java}</b>
 * so the generated controller compiles immediately.
 *
 * <h4>New-module mode (--new-module) — fresh business-* module</h4>
 * <pre>{@code
 * ./mvnw -pl core-bootstrap exec:java \
 *     -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \
 *     -Dexec.args="order --new-module=orders"
 * }</pre>
 * Additionally generates:
 * <ul>
 *   <li>{@code backend/business-orders/pom.xml} — mirrors business-demo's
 *       shape (deps on core-system + core-infrastructure + lombok)</li>
 *   <li>{@code .../security/OrdersPermissions.java} — 4 codes pre-wired
 *       under {@code module="orders"}</li>
 *   <li>Module-local Flyway migration under
 *       {@code backend/business-orders/src/main/resources/db/migration/}</li>
 *   <li>Registers the new module in {@code backend/pom.xml}'s
 *       {@code <modules>} + {@code <dependencyManagement>} sections</li>
 *   <li>Adds a {@code <dependency>} on the new module in
 *       {@code core-bootstrap/pom.xml}</li>
 * </ul>
 * The generated controller references the new {@code &lt;Module&gt;Permissions}
 * class (not {@code DemoPermissions}), so the new module is self-contained.
 *
 * <h3>Refuses to overwrite</h3>
 * Will not touch an existing resource directory or existing module directory.
 * Delete them first to regenerate.
 *
 * <h3>What gets cloned</h3>
 * Both modes copy {@code business-demo/task/*} (5 Java files) and substitute
 * identifiers + table names + package paths. The SQL migration is rendered
 * from an inline minimal template (NOT cloned from V10__demo_task.sql,
 * which carries role/menu/permission seeds we don't want duplicated).
 */
public final class BusinessModuleScaffold {

    private BusinessModuleScaffold() {}

    public static void main(String[] argv) throws Exception {
        if (argv.length == 0 || "--help".equals(argv[0]) || "-h".equals(argv[0])) {
            usage();
            return;
        }
        Args args = Args.parse(argv);
        Path repo = findRepoRoot();
        if (args.newModule != null) {
            new NewModuleMode(repo, args).run();
        } else {
            new LegacyMode(repo, args).run();
        }
    }

    // ───────── arg parsing ─────────────────────────────────────────────

    record Args(String resource, String newModule) {
        static Args parse(String[] argv) {
            String resource = null;
            String newModule = null;
            for (String a : argv) {
                if (a.startsWith("--new-module=")) {
                    newModule = a.substring("--new-module=".length()).toLowerCase();
                } else if (a.startsWith("--")) {
                    die("unknown flag: " + a);
                } else if (resource == null) {
                    resource = a.toLowerCase();
                } else {
                    die("unexpected extra positional arg: " + a);
                }
            }
            if (resource == null) {
                die("resource name required (positional arg). See --help.");
            }
            if (!resource.matches("[a-z][a-z0-9]*")) {
                die("resource must match [a-z][a-z0-9]* — was: " + resource);
            }
            if (newModule != null && !newModule.matches("[a-z][a-z0-9-]*")) {
                die("--new-module value must match [a-z][a-z0-9-]* (RFC1035 label) — was: " + newModule);
            }
            return new Args(resource, newModule);
        }

        String resourceCap()   { return capitalize(resource); }
        String resourceUpper() { return resource.toUpperCase(); }
        String moduleCap()     { return newModule == null ? null : capitalize(stripDashes(newModule)); }
    }

    // ───────── legacy mode (into business-demo) ──────────────────────

    static final class LegacyMode {
        final Path repo;
        final Args args;
        final Path moduleJava;
        final Path templateDir;
        final Path newResourceDir;
        final Path migrationsDir;
        final Path demoPermissionsFile;

        LegacyMode(Path repo, Args args) {
            this.repo = repo;
            this.args = args;
            this.moduleJava = repo.resolve("backend/business-demo/src/main/java/com/platform/business/demo");
            this.templateDir = moduleJava.resolve("task");
            this.newResourceDir = moduleJava.resolve(args.resource);
            // Flyway scans only core-bootstrap/db/migration in legacy mode; new
            // business modules ship their own migrations alongside their code.
            this.migrationsDir = repo.resolve("backend/core-bootstrap/src/main/resources/db/migration");
            this.demoPermissionsFile = moduleJava.resolve("security/DemoPermissions.java");
        }

        void run() throws IOException {
            assertWritable();
            int version = pickNextFlywayVersion(migrationsDir);
            Substitutions sub = legacySubstitutions(args);

            List<String> created = new ArrayList<>();
            cloneJavaFiles(templateDir, newResourceDir, args.resourceCap(), sub, repo, created);
            Path migration = migrationsDir.resolve("V" + version + "__create_business_" + args.resource + ".sql");
            Files.writeString(migration, renderMigration(args.resource, version), StandardCharsets.UTF_8);
            created.add(rel(repo, migration));

            boolean injected = injectDemoPermissions(demoPermissionsFile, args);
            if (injected) {
                created.add(rel(repo, demoPermissionsFile) + " (4 constants appended)");
            }

            printLegacySuccess(args, version, created, injected);
        }

        void assertWritable() {
            if (!Files.isDirectory(templateDir))
                die("template not found: " + templateDir + " — is business-demo/task present?");
            if (Files.exists(newResourceDir))
                die("resource already exists: " + newResourceDir + " — delete it to regenerate");
            if (!Files.isRegularFile(demoPermissionsFile))
                die("DemoPermissions.java not at expected path: " + demoPermissionsFile);
        }
    }

    // ───────── new-module mode ────────────────────────────────────────

    static final class NewModuleMode {
        final Path repo;
        final Args args;
        final Path moduleRoot;
        final Path moduleJava;
        final Path templateDir;
        final Path newResourceDir;
        final Path migrationsDir;
        final Path permissionsFile;
        final String moduleArtifactId;

        NewModuleMode(Path repo, Args args) {
            this.repo = repo;
            this.args = args;
            this.moduleArtifactId = "business-" + args.newModule;
            this.moduleRoot = repo.resolve("backend").resolve(moduleArtifactId);
            this.moduleJava = moduleRoot.resolve("src/main/java/com/platform/business/" + stripDashes(args.newModule));
            // Template lives in business-demo (the reference module).
            this.templateDir = repo.resolve("backend/business-demo/src/main/java/com/platform/business/demo/task");
            this.newResourceDir = moduleJava.resolve(args.resource);
            // Migrations ship with the new module, picked up automatically by
            // Flyway since Spring Boot scans classpath:db/migration across
            // every JAR on the runtime classpath.
            this.migrationsDir = moduleRoot.resolve("src/main/resources/db/migration");
            this.permissionsFile = moduleJava.resolve("security").resolve(args.moduleCap() + "Permissions.java");
        }

        void run() throws IOException {
            assertWritable();
            int version = pickNextFlywayVersion(repo.resolve("backend/core-bootstrap/src/main/resources/db/migration"));
            Substitutions sub = newModuleSubstitutions(args);

            List<String> created = new ArrayList<>();

            // 1. Module pom.xml
            Path pom = moduleRoot.resolve("pom.xml");
            Files.createDirectories(moduleRoot);
            Files.writeString(pom, renderModulePom(moduleArtifactId, args), StandardCharsets.UTF_8);
            created.add(rel(repo, pom));

            // 2. Permissions class
            Files.createDirectories(permissionsFile.getParent());
            Files.writeString(permissionsFile, renderPermissionsClass(args), StandardCharsets.UTF_8);
            created.add(rel(repo, permissionsFile));

            // 3. Resource source files (clone + substitute)
            cloneJavaFiles(templateDir, newResourceDir, args.resourceCap(), sub, repo, created);

            // 4. Module-local migration
            Files.createDirectories(migrationsDir);
            Path migration = migrationsDir.resolve("V" + version + "__create_business_" + args.resource + ".sql");
            Files.writeString(migration, renderMigration(args.resource, version), StandardCharsets.UTF_8);
            created.add(rel(repo, migration));

            // 5. Wire into Maven graph
            boolean updatedBackendPom = registerModuleInBackendPom(repo, moduleArtifactId);
            boolean updatedBootstrapPom = registerDependencyInBootstrapPom(repo, moduleArtifactId);
            if (updatedBackendPom)   created.add(rel(repo, repo.resolve("backend/pom.xml")) + " (modules + dependencyManagement)");
            if (updatedBootstrapPom) created.add(rel(repo, repo.resolve("backend/core-bootstrap/pom.xml")) + " (added dependency)");

            printNewModuleSuccess(args, moduleArtifactId, version, created);
        }

        void assertWritable() {
            if (!Files.isDirectory(templateDir))
                die("template not found: " + templateDir + " — is business-demo/task present?");
            if (Files.exists(moduleRoot))
                die("module already exists: " + moduleRoot + " — delete it to regenerate");
        }
    }

    // ───────── substitution rules ─────────────────────────────────────

    /**
     * Token map applied in INSERTION ORDER (a LinkedHashMap). Longer / more
     * specific keys come first to avoid catastrophic substring overlaps
     * (e.g. {@code demo_task} must be transformed before bare {@code task}).
     *
     * <p>Uses plain {@link String#replace} rather than word-boundary regex.
     * Word-boundary fails on CamelCase identifiers like {@code TaskEntity}
     * (where {@code Task} is followed by another ident char), which is
     * exactly the shape we need to substitute. The {@code business-demo/task}
     * template has been audited — none of the tokens here have problematic
     * substring overlaps in the source files (no "Tasking", no "datastructure"
     * etc).
     */
    record Substitutions(LinkedHashMap<String, String> rules) {
        String apply(String src) {
            String out = src;
            for (Map.Entry<String, String> e : rules.entrySet()) {
                out = out.replace(e.getKey(), e.getValue());
            }
            return out;
        }
    }

    static Substitutions legacySubstitutions(Args args) {
        LinkedHashMap<String, String> r = new LinkedHashMap<>();
        // Order matters — see Substitutions javadoc.
        r.put("demo_task", "business_" + args.resource);     // SQL table — must run before bare 'task'
        r.put("Task",      args.resourceCap());              // class names
        r.put("TASK_",     args.resourceUpper() + "_");      // constant prefix (covers TASK_READ etc)
        r.put("TASK",      args.resourceUpper());            // bare TASK (rare)
        r.put("task",      args.resource);                   // lowercase id (package segment, var names, perm codes)
        return new Substitutions(r);
    }

    static Substitutions newModuleSubstitutions(Args args) {
        LinkedHashMap<String, String> r = new LinkedHashMap<>();
        // Same as legacy plus the module rename.
        r.put("demo_task",       "business_" + args.resource);
        r.put("business.demo",   "business." + stripDashes(args.newModule));   // Java package
        r.put("DemoPermissions", args.moduleCap() + "Permissions");            // imports + references
        r.put("Task",            args.resourceCap());
        r.put("TASK_",           args.resourceUpper() + "_");
        r.put("TASK",            args.resourceUpper());
        r.put("task",            args.resource);
        return new Substitutions(r);
    }

    // ───────── DemoPermissions auto-inject (legacy mode) ─────────────

    /**
     * Append 4 permission constants for the new resource immediately after
     * the last existing {@code *_DELETE} line in DemoPermissions.java. No-op
     * if a constant with the same name already exists (idempotent retry).
     * Returns true iff the file was modified.
     */
    static boolean injectDemoPermissions(Path file, Args args) throws IOException {
        String body = Files.readString(file, StandardCharsets.UTF_8);
        String marker = args.resourceUpper() + "_READ";
        if (body.contains(marker)) {
            return false;   // already injected
        }
        // Find the last `public static final String *_DELETE = ...;` line and insert after it.
        Pattern lastDelete = Pattern.compile(
                "(?m)^(\\s*public static final String\\s+\\w+_DELETE\\s*=\\s*\"[^\"]+\"\\s*;\\s*)$");
        Matcher m = lastDelete.matcher(body);
        int insertAt = -1;
        while (m.find()) {
            insertAt = m.end();   // keep advancing to last match
        }
        if (insertAt < 0) {
            // No existing _DELETE constant — fall back: insert before the
            // static { PermissionCode.registerAll... } block.
            int registerAt = body.indexOf("static {");
            if (registerAt < 0) {
                throw new IOException("DemoPermissions.java has no _DELETE constants and no static block — "
                        + "manual edit required");
            }
            insertAt = body.lastIndexOf('\n', registerAt - 1);
        }

        String R = args.resourceUpper();
        String r = args.resource;
        String inject = "\n"
                + "    public static final String " + pad(R + "_READ")   + " = \"" + r + ":read\";\n"
                + "    public static final String " + pad(R + "_CREATE") + " = \"" + r + ":create\";\n"
                + "    public static final String " + pad(R + "_UPDATE") + " = \"" + r + ":update\";\n"
                + "    public static final String " + pad(R + "_DELETE") + " = \"" + r + ":delete\";";

        String updated = body.substring(0, insertAt) + inject + body.substring(insertAt);
        Files.writeString(file, updated, StandardCharsets.UTF_8);
        return true;
    }

    /** Right-pad a constant name to width 12 so adjacent {@code =} signs line up across constants. */
    private static String pad(String s) {
        return s.length() >= 12 ? s : s + " ".repeat(12 - s.length());
    }

    // ───────── new-module pom.xml + permissions class templates ──────

    static String renderModulePom(String artifactId, Args args) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>com.platform</groupId>
                        <artifactId>core-parent</artifactId>
                        <version>1.0.0-SNAPSHOT</version>
                        <relativePath>../pom.xml</relativePath>
                    </parent>

                    <artifactId>%s</artifactId>
                    <name>%s</name>
                    <description>Auto-generated by BusinessModuleScaffold — replace this with a real description.</description>

                    <dependencies>
                        <dependency>
                            <groupId>com.platform</groupId>
                            <artifactId>core-infrastructure</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.platform</groupId>
                            <artifactId>core-system</artifactId>
                        </dependency>

                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <scope>provided</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(artifactId, artifactId);
    }

    static String renderPermissionsClass(Args args) {
        String R = args.resourceUpper();
        String r = args.resource;
        String module = args.newModule;
        return """
                package com.platform.business.%s.security;

                import com.platform.core.common.security.PermissionCode;
                import org.springframework.stereotype.Component;

                /**
                 * Permission codes for the %s business module. Auto-generated by
                 * BusinessModuleScaffold — add codes for additional resources as
                 * the module grows.
                 *
                 * <p>The {@code @Component} annotation forces Spring to load the
                 * class at startup so the {@code static} block (which calls
                 * {@link PermissionCode#registerAll}) runs before
                 * {@code PermissionConsistencyGuard} validates DB rows.
                 */
                @Component
                public final class %sPermissions {

                    public static final String %s_READ   = "%s:read";
                    public static final String %s_CREATE = "%s:create";
                    public static final String %s_UPDATE = "%s:update";
                    public static final String %s_DELETE = "%s:delete";

                    static {
                        PermissionCode.registerAll(%sPermissions.class, "%s");
                    }

                    %sPermissions() {}
                }
                """.formatted(
                        stripDashes(module),
                        module,
                        args.moduleCap(),
                        R, r, R, r, R, r, R, r,
                        args.moduleCap(), module,
                        args.moduleCap());
    }

    // ───────── parent + bootstrap pom edits ──────────────────────────

    /**
     * Add the new module to backend/pom.xml's {@code <modules>} list and
     * a corresponding {@code <dependency>} declaration to its
     * {@code <dependencyManagement>}. Idempotent — re-running with the same
     * module name leaves both intact.
     */
    static boolean registerModuleInBackendPom(Path repo, String artifactId) throws IOException {
        Path pom = repo.resolve("backend/pom.xml");
        String body = Files.readString(pom, StandardCharsets.UTF_8);
        boolean changed = false;

        // 1) <modules> registration — insert immediately after the
        // <module>business-demo</module> line so the new module sits next to
        // business-demo in the natural read order. core-bootstrap stays last
        // (it depends on every other module, so Maven naturally builds it
        // last via reactor sort anyway).
        String moduleLine = "        <module>" + artifactId + "</module>\n";
        if (!body.contains(moduleLine)) {
            Pattern afterDemo = Pattern.compile("(?m)^(\\s*<module>business-demo</module>\\s*)$");
            Matcher m = afterDemo.matcher(body);
            if (!m.find()) die("could not find <module>business-demo</module> in backend/pom.xml");
            body = body.substring(0, m.end()) + "\n" + moduleLine.stripTrailing()
                    + body.substring(m.end());
            changed = true;
        }

        // 2) <dependencyManagement> entry — pinned at ${project.version} so
        // child modules don't need to spell out the version.
        String depBlock = "            <dependency>\n"
                + "                <groupId>com.platform</groupId>\n"
                + "                <artifactId>" + artifactId + "</artifactId>\n"
                + "                <version>${project.version}</version>\n"
                + "            </dependency>";
        if (!body.contains("<artifactId>" + artifactId + "</artifactId>\n                <version>${project.version}")) {
            // Insert immediately after the business-demo block (4 lines).
            Pattern afterDemoDep = Pattern.compile(
                    "(            <dependency>\\s*\\n"
                  + "                <groupId>com\\.platform</groupId>\\s*\\n"
                  + "                <artifactId>business-demo</artifactId>\\s*\\n"
                  + "                <version>\\$\\{project\\.version\\}</version>\\s*\\n"
                  + "            </dependency>)");
            Matcher m = afterDemoDep.matcher(body);
            if (!m.find()) die("could not find business-demo <dependency> block in backend/pom.xml");
            body = body.substring(0, m.end()) + "\n" + depBlock + body.substring(m.end());
            changed = true;
        }

        if (changed) Files.writeString(pom, body, StandardCharsets.UTF_8);
        return changed;
    }

    /**
     * Add a {@code <dependency>} on the new module to core-bootstrap/pom.xml's
     * {@code <dependencies>} block, immediately after the business-demo entry.
     * Idempotent.
     */
    static boolean registerDependencyInBootstrapPom(Path repo, String artifactId) throws IOException {
        Path pom = repo.resolve("backend/core-bootstrap/pom.xml");
        String body = Files.readString(pom, StandardCharsets.UTF_8);
        String marker = "<artifactId>" + artifactId + "</artifactId>";
        if (body.contains(marker)) return false;

        String depBlock = "        <dependency>\n"
                + "            <groupId>com.platform</groupId>\n"
                + "            <artifactId>" + artifactId + "</artifactId>\n"
                + "        </dependency>";

        // Insert after business-demo's block. The bootstrap pom doesn't pin
        // versions because dependencyManagement in the parent already does.
        Pattern afterDemoDep = Pattern.compile(
                "(        <dependency>\\s*\\n"
              + "            <groupId>com\\.platform</groupId>\\s*\\n"
              + "            <artifactId>business-demo</artifactId>\\s*\\n"
              + "        </dependency>)");
        Matcher m = afterDemoDep.matcher(body);
        if (!m.find()) die("could not find business-demo <dependency> in core-bootstrap/pom.xml");
        body = body.substring(0, m.end()) + "\n" + depBlock + body.substring(m.end());
        Files.writeString(pom, body, StandardCharsets.UTF_8);
        return true;
    }

    // ───────── shared file-cloning helpers ────────────────────────────

    static void cloneJavaFiles(Path srcDir, Path destDir, String resourceCap,
                               Substitutions sub, Path repo, List<String> created) throws IOException {
        for (Path src : listJavaFilesRecursive(srcDir)) {
            Path rel = srcDir.relativize(src);
            // entity/TaskEntity.java → entity/<Resource>Entity.java
            String newFilename = rel.getFileName().toString().replace("Task", resourceCap);
            Path dest = destDir
                    .resolve(rel.getParent() == null ? Paths.get("") : rel.getParent())
                    .resolve(newFilename);
            Files.createDirectories(dest.getParent());
            String content = Files.readString(src, StandardCharsets.UTF_8);
            Files.writeString(dest, sub.apply(content), StandardCharsets.UTF_8);
            created.add(rel(repo, dest));
        }
    }

    static List<Path> listJavaFilesRecursive(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        Files.walk(dir).filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(out::add);
        return out;
    }

    static int pickNextFlywayVersion(Path migrations) throws IOException {
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
        // Convention: business migrations start at V1000+; framework owns V1-V999.
        return Math.max(max + 1, 1000);
    }

    static String renderMigration(String resource, int version) {
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

    // ───────── helpers ────────────────────────────────────────────────

    static Path findRepoRoot() {
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

    static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Strip dashes for use in Java identifiers — {@code multi-word} → {@code multiword}. */
    static String stripDashes(String s) {
        return s.replace("-", "");
    }

    static String rel(Path repo, Path file) {
        return repo.relativize(file).toString().replace('\\', '/');
    }

    static void usage() {
        System.out.println("""
                Usage:
                  ./mvnw -pl core-bootstrap exec:java \\
                      -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \\
                      -Dexec.args="<resource> [--new-module=<module>]"

                Examples:

                  # Quick scaffold into business-demo (auto-injects perms into DemoPermissions)
                  -Dexec.args="invoice"

                  # Brand-new business module + auto-wired perms class + Maven graph updates
                  -Dexec.args="order --new-module=orders"

                Constraints:
                  <resource> must match [a-z][a-z0-9]*           — e.g. order, invoice, salesreport
                  <module>   must match [a-z][a-z0-9-]*          — e.g. orders, sales-reports

                Refuses to overwrite an existing resource directory or module directory.
                """);
    }

    static void die(String msg) {
        System.err.println("ERROR: " + msg);
        System.exit(1);
    }

    // ───────── output ────────────────────────────────────────────────

    static void printLegacySuccess(Args args, int version, List<String> created, boolean permsInjected) {
        System.out.println();
        System.out.println("✓ Scaffolded resource '" + args.resource + "' (" + args.resourceCap()
                + ") at V" + version + " — legacy mode (into business-demo)");
        System.out.println("──────────────────────────────────────────────────────────────");
        System.out.println("Created:");
        for (String f : created) System.out.println("  " + f);
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Edit V" + version + "__create_business_" + args.resource
                + ".sql — replace placeholder columns with your business shape");
        System.out.println("  2. Edit " + args.resourceCap() + "Entity.java + "
                + args.resourceCap() + "Dto.java to match");
        if (permsInjected) {
            System.out.println("  3. (auto) 4 permission constants added to DemoPermissions.java");
        } else {
            System.out.println("  3. DemoPermissions.java already had " + args.resourceUpper()
                    + "_* constants — nothing to inject");
        }
        System.out.println("  4. Implement TODO bodies in " + args.resourceCap() + "Service.java");
        System.out.println("  5. ./mvnw test       — ArchitectureTest will verify conventions");
        System.out.println("  6. ./mvnw spring-boot:run  — TenantSchemaGuard verifies the migration");
        System.out.println();
        System.out.println("Reference: backend/business-demo/task/");
        System.out.println();
    }

    static void printNewModuleSuccess(Args args, String moduleArtifactId, int version, List<String> created) {
        System.out.println();
        System.out.println("✓ Scaffolded module '" + moduleArtifactId + "' with resource '"
                + args.resource + "' (" + args.resourceCap() + ") at V" + version + " — new-module mode");
        System.out.println("──────────────────────────────────────────────────────────────");
        System.out.println("Created / updated:");
        for (String f : created) System.out.println("  " + f);
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Edit V" + version + "__create_business_" + args.resource
                + ".sql — replace placeholder columns with your business shape");
        System.out.println("  2. Edit " + args.resourceCap() + "Entity.java + "
                + args.resourceCap() + "Dto.java to match");
        System.out.println("  3. Implement TODO bodies in " + args.resourceCap() + "Service.java");
        System.out.println("  4. ./mvnw install -DskipTests   — Maven build the new module + reactor");
        System.out.println("  5. ./mvnw test       — ArchitectureTest will verify conventions");
        System.out.println("  6. ./mvnw spring-boot:run  — TenantSchemaGuard verifies the migration");
        System.out.println();
        System.out.println("The new module is self-contained:");
        System.out.println("  - Permission codes auto-registered via " + args.moduleCap() + "Permissions");
        System.out.println("  - Migration lives in the module's own db/migration/ — Flyway picks it up via classpath scan");
        System.out.println("  - Wired into backend/pom.xml and core-bootstrap/pom.xml");
        System.out.println();
    }
}
