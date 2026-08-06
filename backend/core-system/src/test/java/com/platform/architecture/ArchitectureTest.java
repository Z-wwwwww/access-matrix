package com.platform.architecture;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.core.common.security.RequiresPermission;
import com.platform.core.infrastructure.persistence.BaseEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture-level invariants enforced at test time.
 *
 * <p>The rules catch the mistakes a new contributor (human or AI) is
 * most likely to make when adding a business module — forgetting
 * {@code BaseEntity} on a new entity, forgetting {@code @RequiresPermission}
 * on a new endpoint, putting a mapper or controller in the wrong package,
 * letting a service depend on a controller, sneaking {@code @InterceptorIgnore}
 * into business code.
 *
 * <p>Implemented via Spring's {@code ClassPathScanningCandidateComponentProvider}
 * because ArchUnit's importer turned out to be unreliable on this project's
 * Maven multi-module + JDK 25 classpath shape (consistently 0 classes
 * imported). Spring's scanner is already on the classpath, well-tested, and
 * is the same mechanism Spring itself uses to discover {@code @Component}s.
 */
public class ArchitectureTest {

    private static final String ROOT_PACKAGE = "com.platform";

    /** Entities that intentionally skip {@link BaseEntity}'s shape. Keep this list small + commented. */
    private static final Set<String> ENTITIES_WITHOUT_BASE_ENTITY_OK = Set.of(
            "LoginLogEntity",            // append-only audit log, no soft-delete
            "OpLogEntity",               // append-only audit log
            "UserInviteEntity",          // single-use token; token IS the auth, no tenant_id needed
            "PasswordResetTokenEntity",  // same single-use token shape
            "DomainEventEntity",         // append-only event store / outbox (V36); no soft-delete, like OpLogEntity
            "MenuEntity",                // single GLOBAL menu set (V41/V43); no tenant_id by design (declares its own mark+audit)
            "DictEntity",                // single GLOBAL managed-dict set (V44); no tenant_id by design, like MenuEntity
            "DictItemEntity"             // dict items, same GLOBAL shape as DictEntity
    );

    /** Controllers where every HTTP method is a public/pre-auth endpoint by design. */
    private static final Set<String> PUBLIC_CONTROLLERS = Set.of(
            "AuthController",            // login / refresh / logout
            "AdminAuthController",       // break-glass HS256 login
            "HealthController",          // readiness probe
            "InviteController",          // token-URL invite accept
            "PasswordResetController",   // token-URL password reset
            "MeMenuController",          // /me/menus — JWT IS the auth
            "MePermissionController",    // /me/permissions — same
            "UserController",            // /user/me — same
            "ScopeMeController",         // /scope/me — same
            "NotificationController",    // /notification/* — personal inbox, JWT-is-auth like /menu/me
            "DictController"             // /dict/{code} — dropdown data for any logged-in user, JWT-is-auth like /menu/me
    );

    private static List<Class<?>> entities;
    private static List<Class<?>> mappers;
    private static List<Class<?>> controllers;
    private static List<Class<?>> serviceClasses;

    @BeforeAll
    static void scanClasspath() {
        entities       = findByAnnotation(TableName.class);
        controllers    = findByAnnotation(RestController.class);
        mappers        = findByAssignable(BaseMapper.class);
        serviceClasses = findByPackageFragment(".service.");
    }

    // ─── smoke ────────────────────────────────────────────────────────

    @Test
    @DisplayName("smoke: classpath scan finds something — guards against silent empty-corpus passes")
    void smoke_scanFindsSomething() {
        assertThat(entities).isNotEmpty();
        assertThat(controllers).isNotEmpty();
        assertThat(mappers).isNotEmpty();
    }

    // ─── entities ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Every @TableName entity must extend BaseEntity (allowlist for append-only tables)")
    void entities_must_extend_BaseEntity() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> e : entities) {
            if (ENTITIES_WITHOUT_BASE_ENTITY_OK.contains(e.getSimpleName())) continue;
            if (!BaseEntity.class.isAssignableFrom(e)) {
                offenders.add(e.getName());
            }
        }
        assertThat(offenders)
                .as("Entities annotated with @TableName that don't extend BaseEntity. "
                        + "BaseEntity provides tenant_id + audit fields; bypassing it bypasses tenant isolation. "
                        + "If your entity is an append-only audit table or single-use token, add its simple name "
                        + "to ENTITIES_WITHOUT_BASE_ENTITY_OK in ArchitectureTest.")
                .isEmpty();
    }

    // ─── controllers ──────────────────────────────────────────────────

    @Test
    @DisplayName("Every HTTP-mapped controller method must have @RequiresPermission or live in a public controller")
    void rest_endpoints_must_have_requires_permission() {
        Class<? extends Annotation>[] mappingAnnotations = mappingAnnotations();

        List<String> offenders = new ArrayList<>();
        for (Class<?> c : controllers) {
            if (PUBLIC_CONTROLLERS.contains(c.getSimpleName())) continue;
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) continue;
                boolean isEndpoint = false;
                for (Class<? extends Annotation> a : mappingAnnotations) {
                    if (m.isAnnotationPresent(a)) { isEndpoint = true; break; }
                }
                if (!isEndpoint) continue;
                if (!m.isAnnotationPresent(RequiresPermission.class)) {
                    offenders.add(c.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertThat(offenders)
                .as("Public HTTP endpoints without @RequiresPermission. Either annotate the method with "
                        + "@RequiresPermission(SomePermissions.X) using a constant from a *Permissions class, "
                        + "or — if the endpoint is genuinely pre-auth / token-URL / readiness — add the "
                        + "controller's simple name to PUBLIC_CONTROLLERS in ArchitectureTest.")
                .isEmpty();
    }

    @Test
    @DisplayName("@RestController classes must live in a .controller package")
    void controllers_must_reside_in_controller_packages() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> c : controllers) {
            // HealthController is its own .health package — readiness probes
            // conventionally sit at the top of their module, not under a
            // .controller subfolder. Single hardcoded exception.
            if (c.getSimpleName().equals("HealthController")) continue;
            if (!c.getPackageName().contains(".controller")) {
                offenders.add(c.getName());
            }
        }
        assertThat(offenders).isEmpty();
    }

    // ─── mappers ──────────────────────────────────────────────────────

    @Test
    @DisplayName("BaseMapper subtypes must live in a .mapper package (so @MapperScan picks them up)")
    void mappers_must_reside_in_mapper_packages() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> m : mappers) {
            if (!m.getPackageName().contains(".mapper")) {
                offenders.add(m.getName());
            }
        }
        assertThat(offenders)
                .as("@MapperScan(\"com.platform.**.mapper\") in MybatisPlusConfig only scans .mapper packages.")
                .isEmpty();
    }

    // ─── layering ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Service classes must not import from .controller packages")
    void services_must_not_depend_on_controllers() {
        // Static dependency check on services. The Spring scanner gives us
        // the classes; we read declared field / method types to find
        // any reference into the controller layer.
        List<String> offenders = new ArrayList<>();
        for (Class<?> svc : serviceClasses) {
            for (Class<?> dep : declaredTypeDependencies(svc)) {
                if (dep.getPackageName().contains(".controller")) {
                    offenders.add(svc.getSimpleName() + " → " + dep.getSimpleName());
                }
            }
        }
        assertThat(offenders)
                .as("The dependency arrow goes controller → service, never the other way. "
                        + "If a service needs to share a record with a controller, lift it into a shared dto package.")
                .isEmpty();
    }

    // ─── bypasses ─────────────────────────────────────────────────────

    @Test
    @DisplayName("No business code uses @InterceptorIgnore (it bypasses tenant scoping)")
    void business_code_must_not_use_interceptor_ignore() {
        List<String> offenders = new ArrayList<>();
        // Search all entities, mappers, services, controllers under com.platform.business.*.
        List<Class<?>> business = new ArrayList<>();
        business.addAll(entities);
        business.addAll(mappers);
        business.addAll(serviceClasses);
        business.addAll(controllers);

        for (Class<?> c : business) {
            if (!c.getPackageName().startsWith("com.platform.business")) continue;
            if (c.isAnnotationPresent(InterceptorIgnore.class)) {
                offenders.add(c.getName() + " (class-level)");
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(InterceptorIgnore.class)) {
                    offenders.add(c.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertThat(offenders)
                .as("@InterceptorIgnore is reserved for framework-level cross-tenant queries. "
                        + "Business code must not use it — every tenant scope must be enforced.")
                .isEmpty();
    }

    // ─── module boundaries ────────────────────────────────────────────
    //
    // These two were listed in backend/AGENTS.md as already enforced here
    // ("forbid reverse deps, forbid business-* from using core_* Mappers")
    // but no such rule existed — the guard everyone was told to rely on was
    // never written, so the layering could erode with a green build. They read
    // SOURCE files rather than loaded classes because an import is exactly what
    // they are about, and imports do not survive into the class file when the
    // type is only referenced in a signature the scanner doesn't reach.

    /** Module directories (not their src roots) whose name starts with the prefix. */
    private static List<Path> moduleDirs(String namePrefix) {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        try (var dirs = Files.list(backend)) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(namePrefix))
                    .filter(p -> Files.isDirectory(p.resolve("src/main/java")))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot list backend modules from " + backend, e);
        }
    }

    private static List<Path> moduleSources(String namePrefix) {
        return moduleDirs(namePrefix).stream().map(p -> p.resolve("src/main/java")).toList();
    }

    private static List<Path> javaFiles(Path root) {
        try (var s = Files.walk(root)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + root, e);
        }
    }

    /** Every import line of a source file. */
    private static List<String> imports(Path javaFile) {
        try {
            return Files.readAllLines(javaFile, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(l -> l.startsWith("import "))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + javaFile, e);
        }
    }

    @Test
    @DisplayName("Module dependencies only flow downward (no reverse deps between core-*/business-*)")
    void module_dependencies_only_flow_downward() {
        // module dir prefix → package prefixes it must never import
        Map<String, List<String>> forbidden = Map.of(
                "core-common", List.of("com.platform.core.infrastructure",
                                       "com.platform.system",
                                       "com.platform.business"),
                "core-infrastructure", List.of("com.platform.system",
                                               "com.platform.business"),
                "core-system", List.of("com.platform.business")
        );

        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (var e : forbidden.entrySet()) {
            for (Path dir : moduleDirs(e.getKey())) {
                // moduleDirs matches by prefix; require the exact module name so a
                // future "core-commons" can't quietly inherit core-common's rules.
                if (!dir.getFileName().toString().equals(e.getKey())) continue;
                for (Path f : javaFiles(dir.resolve("src/main/java"))) {
                    scanned++;
                    for (String imp : imports(f)) {
                        for (String bad : e.getValue()) {
                            if (imp.startsWith("import " + bad + ".")) {
                                offenders.add(e.getKey() + " → " + imp + "  (" + f.getFileName() + ")");
                            }
                        }
                    }
                }
            }
        }
        assertThat(scanned).as("boundary scan found no sources — the module layout moved").isPositive();
        assertThat(offenders)
                .as("Dependencies flow one way only: core-bootstrap → core-system & business-* → "
                        + "core-infrastructure → core-common. A reverse import makes the lower layer "
                        + "un-reusable and is the first step to a cycle.")
                .isEmpty();
    }

    /**
     * Business modules talk to the platform through its SERVICES, never by reaching
     * into {@code core_*} tables with core mappers: a core mapper carries the core
     * module's own tenant/soft-delete assumptions, and business code binding to it
     * silently couples a business schema change to the platform's.
     */
    private static final Set<String> BUSINESS_CORE_MAPPER_OK = Set.of(
            // Dev-only demo data seeding (@Profile("dev")): it fabricates the five
            // data-scope demo users + their role links, which by definition live in
            // core_auth_user / core_rbac_user_role. Not runtime business logic, and
            // NOT part of what BusinessModuleScaffold clones (it clones task/ only).
            "DemoSeeder.java"
    );

    @Test
    @DisplayName("business-* must not use core_* Mappers (go through core services instead)")
    void business_modules_must_not_use_core_mappers() {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (Path root : moduleSources("business-")) {
            for (Path f : javaFiles(root)) {
                scanned++;
                if (BUSINESS_CORE_MAPPER_OK.contains(f.getFileName().toString())) continue;
                for (String imp : imports(f)) {
                    if (imp.matches("import com\\.platform\\.system\\..*\\.mapper\\..*;")) {
                        offenders.add(f.getFileName() + " → " + imp);
                    }
                }
            }
        }
        assertThat(scanned).as("no business module sources found — the module layout moved").isPositive();
        assertThat(offenders)
                .as("Business code must reach platform data through core SERVICES (e.g. DictQueryService), "
                        + "not core mappers. Add to BUSINESS_CORE_MAPPER_OK only with a written reason.")
                .isEmpty();
    }

    // ─── time model ───────────────────────────────────────────────────

    @Test
    @DisplayName("Timestamps are instants: no java.time.LocalDateTime in entities/DTOs/services/controllers")
    void timestamps_must_be_instants_not_local_date_time() {
        List<Class<?>> corpus = new ArrayList<>();
        corpus.addAll(entities);
        corpus.addAll(serviceClasses);
        corpus.addAll(controllers);
        corpus.addAll(findByPackageFragment(".dto."));

        List<String> offenders = new ArrayList<>();
        for (Class<?> c : corpus) {
            for (Class<?> dep : declaredTypeDependencies(c)) {
                if (dep == java.time.LocalDateTime.class) {
                    offenders.add(c.getSimpleName());
                }
            }
            // DTO containers hold their records as nested classes (JobDto.View etc.)
            for (Class<?> nested : c.getDeclaredClasses()) {
                for (Class<?> dep : declaredTypeDependencies(nested)) {
                    if (dep == java.time.LocalDateTime.class) {
                        offenders.add(c.getSimpleName() + "." + nested.getSimpleName());
                    }
                }
            }
        }
        assertThat(offenders)
                .as("LocalDateTime is a zone-less wall clock — its meaning silently depends on the "
                        + "writing JVM's default timezone (the pre-V58 bug class). Timestamps must be "
                        + "OffsetDateTime (timestamptz column, ISO-with-offset on the wire). LocalDate/"
                        + "LocalTime stay fine for true calendar concepts; wall-clock decisions go "
                        + "through AppTime.zone().")
                .isEmpty();
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private static List<Class<?>> findByAnnotation(Class<? extends Annotation> ann) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ann));
        return load(scanner.findCandidateComponents(ROOT_PACKAGE));
    }

    private static List<Class<?>> findByAssignable(Class<?> type) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    // Default scanner skips interfaces; mappers ARE interfaces, so override.
                    @Override
                    protected boolean isCandidateComponent(
                            org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isIndependent();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(type));
        return load(scanner.findCandidateComponents(ROOT_PACKAGE)).stream()
                .filter(c -> !c.equals(type))   // exclude the type itself
                .toList();
    }

    private static List<Class<?>> findByPackageFragment(String fragment) {
        // Spring's scanner needs an inclusion filter to return anything — use a
        // catch-all "Object" filter and post-filter in Java.
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(
                            org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isIndependent()
                                && !beanDefinition.getMetadata().isInterface();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        List<Class<?>> all = load(scanner.findCandidateComponents(ROOT_PACKAGE));
        return all.stream()
                .filter(c -> c.getPackageName().contains(fragment))
                .toList();
    }

    private static List<Class<?>> load(Set<BeanDefinition> defs) {
        List<Class<?>> out = new ArrayList<>(defs.size());
        for (BeanDefinition d : defs) {
            try {
                out.add(Class.forName(d.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                // Skip — class was returned by metadata reader but not loadable
                // (rare; usually a generated proxy). Real source classes always load.
            }
        }
        return out;
    }

    private static Set<Class<?>> declaredTypeDependencies(Class<?> c) {
        Set<Class<?>> deps = new LinkedHashSet<>();
        // fields
        for (var f : c.getDeclaredFields()) deps.add(f.getType());
        // method parameters + return types
        for (var m : c.getDeclaredMethods()) {
            deps.add(m.getReturnType());
            for (var p : m.getParameterTypes()) deps.add(p);
        }
        // constructor parameters
        for (var con : c.getDeclaredConstructors()) {
            for (var p : con.getParameterTypes()) deps.add(p);
        }
        deps.remove(void.class);
        deps.removeIf(Class::isPrimitive);
        return deps;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation>[] mappingAnnotations() {
        return new Class[] {
                GetMapping.class, PostMapping.class, PutMapping.class,
                DeleteMapping.class, PatchMapping.class
        };
    }
}
