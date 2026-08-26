package com.platform.architecture;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.core.common.security.RequiresPermission;
import com.platform.core.infrastructure.persistence.BaseEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

    /**
     * Endpoints that carry no {@code @RequiresPermission} by design — listed
     * <b>one method at a time</b>, as {@code SimpleClassName#methodName}.
     *
     * <p>This used to be a set of controller <em>class</em> names, which made
     * the exemption inheritable: once a class was listed, every method it would
     * ever grow was silently exempt too. {@code AdminAuthController} was on that
     * list for a break-glass HS256 login endpoint that has since been deleted —
     * so the platform's most privileged controller ({@code /admin/auth/unlock},
     * {@code /admin/auth/force-logout} — the latter gated on {@code *:*}) sat
     * under a blanket exemption, and a new unannotated method there would have
     * been reachable by <em>any authenticated user of any tenant</em>
     * ({@code SecurityConfig} authenticates everything off {@code PERMIT_PATHS},
     * but authorization comes only from {@code @RequiresPermission}).
     * Verified before the change by adding an unannotated
     * {@code POST /admin/auth/__probe}: the rule stayed green.
     *
     * <p>Method granularity means adding an endpoint to a controller that
     * happens to host a public one is a deliberate, reviewable line here.
     * {@link #public_endpoint_allowlist_must_not_rot()} keeps the list honest
     * in the other direction.
     */
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            // pre-auth: no JWT yet
            "AuthController#login",
            "AuthController#refresh",
            "AuthController#logout",
            "HealthController#health",
            // token-URL flows — the single-use token IS the credential
            "InviteController#probe",
            "InviteController#accept",
            "PasswordResetController#probe",
            "PasswordResetController#accept",
            // "about me" reads: the JWT IS the authorization, the response is
            // scoped to the caller and carries no other user's data
            "MeMenuController#me",
            "MePermissionController#me",
            "UserController#me",
            "UserController#updateMyProfile",
            "ScopeMeController#me",
            // personal notification inbox — every handler is caller-scoped
            "NotificationController#sseTicket",
            "NotificationController#stream",
            "NotificationController#unreadCount",
            "NotificationController#list",
            "NotificationController#read",
            "NotificationController#readAll",
            // dropdown data for any logged-in user
            "DictController#get"
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

    @Test
    @DisplayName("smoke: the corpus covers EVERY module's main sources (classpath scan vs source scan)")
    void scan_corpus_covers_every_module() {
        Set<String> scannedControllers = simpleNames(controllers);
        Set<String> scannedEntities = simpleNames(entities);

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : declaredInSources().entrySet()) {
            String simpleName = e.getKey();
            String kind = e.getValue();
            boolean found = "controller".equals(kind)
                    ? scannedControllers.contains(simpleName)
                    : scannedEntities.contains(simpleName);
            if (!found) missing.add(kind + " " + simpleName);
        }

        // Everything above this line is only as good as the corpus it runs over,
        // and the corpus is a CLASSPATH scan — so it silently shrinks for reasons
        // that have nothing to do with the rules:
        //
        //   1. This test used to live in core-system, which does NOT depend on
        //      business-demo (only core-bootstrap does). TaskController and
        //      TaskEntity — the reference pair BusinessModuleScaffold clones for
        //      every new module, i.e. exactly the population the javadoc above
        //      says these rules exist to protect — were invisible to all of them.
        //      Measured: 24 controllers / 20 entities scanned, zero from business-*.
        //   2. Spring's stock scanner evaluates @Conditional*, which dropped 15
        //      more classes (see FullCorpusScanner).
        //
        // smoke_scanFindsSomething can't see either: the corpus was never empty,
        // just short. Comparing against the SOURCE TREE is what makes "short"
        // detectable, and it fails on the next business module too — no edit here.
        assertThat(missing)
                .as("Types declared in a module's main sources that the classpath scan never saw. "
                        + "The rules in this class silently skip them. Usual cause: this test's module "
                        + "doesn't depend on the module that declares them — it must sit at the TOP of "
                        + "the dependency graph (core-bootstrap), not beside the code it checks.")
                .isEmpty();
    }

    private static Set<String> simpleNames(List<Class<?>> classes) {
        Set<String> out = new LinkedHashSet<>();
        for (Class<?> c : classes) out.add(c.getSimpleName());
        return out;
    }

    /** simple class name → "controller" | "entity", read from every module's {@code src/main/java}. */
    private static Map<String, String> declaredInSources() {
        // ^\s*@ anchors to a real type annotation: those sit alone on a line,
        // while a javadoc mention is always preceded by the block's '*'
        // (PermissionConsistencyGuard's "{@code @RestController}" was matched by
        // the first version of this and reported as a missing controller).
        // The negative lookahead keeps @RestControllerAdvice
        // (GlobalExceptionHandler) from counting as a controller.
        Pattern controller = Pattern.compile("(?m)^\\s*@RestController(?![A-Za-z0-9_])");
        Pattern entity = Pattern.compile("(?m)^\\s*@TableName(?![A-Za-z0-9_])");

        Path backend = Path.of("..").toAbsolutePath().normalize();
        Map<String, String> out = new LinkedHashMap<>();
        try (var modules = Files.list(backend)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) continue;
                try (var files = Files.walk(main)) {
                    for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                        String src = Files.readString(f, StandardCharsets.UTF_8);
                        String simpleName = f.getFileName().toString().replace(".java", "");
                        if (controller.matcher(src).find()) out.put(simpleName, "controller");
                        else if (entity.matcher(src).find()) out.put(simpleName, "entity");
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot scan module sources under " + backend, e);
        }
        assertThat(out).as("source scan found nothing — did the module layout change?").isNotEmpty();
        return out;
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
        for (Map.Entry<String, Method> e : httpEndpoints(mappingAnnotations).entrySet()) {
            if (PUBLIC_ENDPOINTS.contains(e.getKey())) continue;
            if (!e.getValue().isAnnotationPresent(RequiresPermission.class)) {
                offenders.add(e.getKey());
            }
        }
        assertThat(offenders)
                .as("HTTP endpoints without @RequiresPermission. Everything outside SecurityConfig's "
                        + "PERMIT_PATHS is authenticated but NOT authorized — an unannotated endpoint is "
                        + "callable by any logged-in user of any tenant. Either annotate the method with "
                        + "@RequiresPermission(SomePermissions.X) using a constant from a *Permissions class, "
                        + "or — if the endpoint is genuinely pre-auth / token-URL / readiness / caller-scoped "
                        + "— add THAT METHOD (not its controller) to PUBLIC_ENDPOINTS in ArchitectureTest.")
                .isEmpty();
    }

    @Test
    @DisplayName("The public-endpoint allowlist must not rot (no entries for methods that are gone or now gated)")
    void public_endpoint_allowlist_must_not_rot() {
        Map<String, Method> endpoints = httpEndpoints(mappingAnnotations());

        List<String> vanished = new ArrayList<>();
        List<String> nowGated = new ArrayList<>();
        for (String entry : PUBLIC_ENDPOINTS) {
            Method m = endpoints.get(entry);
            if (m == null) {
                vanished.add(entry);
            } else if (m.isAnnotationPresent(RequiresPermission.class)) {
                nowGated.add(entry);
            }
        }

        // This is the half that was missing. The old class-level allowlist had
        // no way to notice that AdminAuthController's only public endpoint had
        // been deleted, so the exemption outlived the reason for it and stayed
        // pointed at a controller where every remaining method is admin-only.
        assertThat(vanished)
                .as("PUBLIC_ENDPOINTS entries that match no HTTP endpoint any more — the method was "
                        + "renamed or deleted. Drop the entry; leaving it means the allowlist is "
                        + "documenting an exemption nobody needs.")
                .isEmpty();
        assertThat(nowGated)
                .as("PUBLIC_ENDPOINTS entries whose method now HAS @RequiresPermission. The exemption is "
                        + "dead weight — drop the entry so the list keeps meaning 'deliberately ungated'.")
                .isEmpty();
    }

    /** Every {@code SimpleClassName#methodName} → method that is HTTP-mapped on a {@code @RestController}. */
    private static Map<String, Method> httpEndpoints(Class<? extends Annotation>[] mappingAnnotations) {
        Map<String, Method> out = new LinkedHashMap<>();
        for (Class<?> c : controllers) {
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) continue;
                for (Class<? extends Annotation> a : mappingAnnotations) {
                    if (m.isAnnotationPresent(a)) {
                        out.put(c.getSimpleName() + "#" + m.getName(), m);
                        break;
                    }
                }
            }
        }
        return out;
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

    /**
     * Mapper statements that carry their OWN {@code tenant_id = #{...}} predicate
     * and are known to be safe because the ambient {@code RequestContext} names the
     * same tenant. Listed one method at a time, as
     * {@code SimpleClassName#methodName}.
     *
     * <p>The rule below exists because "hand-written SQL is not rewritten by the
     * tenant interceptor" is FALSE and was written into several javadocs as though
     * it were true. MyBatis-Plus rewrites every statement it can parse, so a
     * hand-written {@code @Select} gets {@code AND tenant_id =
     * <RequestContext.tenantId()>} appended ON TOP of its own predicate. Two
     * predicates that name different tenants match nothing — and a thread with no
     * context at all is worse than unscoped, because the handler falls back to
     * {@code demo} and the statement silently reads the WRONG tenant.
     *
     * <p>It has bitten three times: the invite / password-reset token lookups (fixed
     * with {@code @InterceptorIgnore}), the user lookup that the reset flow and
     * {@code AuthService.refresh} drive off a token's tenant (fixed by
     * re-establishing the context), and the notification unread count that the
     * header-less SSE stream reads (fixed with {@code @InterceptorIgnore}). So every
     * new statement of this shape must make a deliberate choice: annotate it, or add
     * it here with the reason its two predicates agree.
     */
    private static final Set<String> TENANT_PREDICATE_MATCHES_CONTEXT = Set.of(
            // Login / OIDC-JIT lookups. CoreRequestContextFilter sets the context from
            // the JWT `tid` (or the X-Tenant-Id header pre-auth) BEFORE these run, and
            // the callers pass that same value — which is exactly why that ordering in
            // the filter is load-bearing.
            "UserMapper#findByIdentifier",
            "UserMapper#findByKeycloakIdAndTenant",
            "UserMapper#countDeletedByKeycloakIdAndTenant",
            "UserMapper#findByUsernameAndTenant",
            "PasswordResetTokenMapper#countConsumedByUser",
            // The one lookup whose tenant argument deliberately differs from the
            // request's. Both callers (AuthService.refresh, from the refresh-token
            // payload; PasswordResetController.accept, from the consumed reset token)
            // re-establish RequestContext on that tenant first, so the injected
            // predicate agrees. See the method's own javadoc.
            "UserMapper#findByIdAndTenant",
            // RBAC reads, all driven from a request already scoped to that tenant.
            "DeptMapper#findSubtreeIds",
            "DeptMapper#findSubtreeIdsAnyStatus",
            "DeptMapper#findAllForTenant",
            "DeptMapper#reRootDescendants",
            "MenuMapper#findMenusByUserId",
            "PermissionMapper#findPermissionCodesByUserId",
            "RoleDeptMapper#findDeptIdsByRoleId",
            "RoleDeptMapper#findActiveDeptIdsByRoleId",
            "RoleMapper#findRoleIdsByUserId",
            "RoleMapper#findRolesByUserId",
            "RoleMenuMapper#findActiveMenuIdsByRoleId",
            "RolePermissionMapper#findActivePermissionIdsByRoleId",
            "UserRoleMapper#findUserIdsByRoleId",
            "UserRoleMapper#findActiveRoleIdsByUserId",
            "UserRoleMapper#existsActiveLink",
            "UserRoleMapper#countActiveHoldersByRoleId",
            "UserRoleMapper#countLiveRoles",
            // Scheduler config, read/written only under the 'system' tenant, for which
            // MybatisPlusConfig.ignoreTable bypasses injection on every table.
            "CoreJobMapper#findAnyByCode",
            "CoreJobMapper#revive"
    );

    /** The SQL a MyBatis statement annotation carries, or "" when there is none. */
    private static String statementSql(Method m) {
        StringBuilder sb = new StringBuilder();
        for (Annotation a : m.getAnnotations()) {
            String n = a.annotationType().getName();
            if (!n.startsWith("org.apache.ibatis.annotations.")) continue;
            String simple = a.annotationType().getSimpleName();
            if (!simple.equals("Select") && !simple.equals("Update")
                    && !simple.equals("Insert") && !simple.equals("Delete")) continue;
            try {
                for (String part : (String[]) a.annotationType().getMethod("value").invoke(a)) {
                    sb.append(part).append('\n');
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot read SQL off " + m, e);
            }
        }
        return sb.toString();
    }

    /** Names bound with {@code @Param} on this method. */
    private static Set<String> paramNames(Method m) {
        Set<String> out = new LinkedHashSet<>();
        for (Annotation[] anns : m.getParameterAnnotations()) {
            for (Annotation a : anns) {
                if (!(a instanceof org.apache.ibatis.annotations.Param p)) continue;
                out.add(p.value());
            }
        }
        return out;
    }

    @Test
    @DisplayName("Hand-written tenant-scoped mapper statements declare how they survive the tenant interceptor")
    void handwritten_tenant_scoped_statements_declare_their_stance() {
        List<String> undeclared = new ArrayList<>();
        int inspected = 0;
        for (Class<?> mapper : mappers) {
            for (Method m : mapper.getDeclaredMethods()) {
                String sql = statementSql(m);
                if (sql.isEmpty()) continue;
                Set<String> params = paramNames(m);
                boolean scopesItself = params.stream()
                        .filter(n -> n.equals("tenantId") || n.equals("tenantCode"))
                        .anyMatch(n -> sql.contains("tenant_id = #{" + n + "}"));
                if (!scopesItself) continue;
                inspected++;
                String key = mapper.getSimpleName() + "#" + m.getName();
                boolean ignores = m.isAnnotationPresent(InterceptorIgnore.class)
                        && "true".equals(m.getAnnotation(InterceptorIgnore.class).tenantLine());
                if (!ignores && !TENANT_PREDICATE_MATCHES_CONTEXT.contains(key)) {
                    undeclared.add(key);
                }
            }
        }
        assertThat(inspected)
                .as("the detector must actually find statements — a rename of the @Param "
                        + "convention would make this rule silently vacuous")
                .isGreaterThan(10);
        assertThat(undeclared)
                .as("These statements carry their own tenant_id predicate, and the tenant "
                        + "interceptor appends a SECOND one from RequestContext (it rewrites "
                        + "hand-written SQL too). Either add @InterceptorIgnore(tenantLine=\"true\") "
                        + "when the argument is authoritative, or list the method in "
                        + "TENANT_PREDICATE_MATCHES_CONTEXT with the reason the two agree.")
                .isEmpty();
    }

    @Test
    @DisplayName("The tenant-predicate allowlist does not rot")
    void tenant_predicate_allowlist_must_not_rot() {
        Set<String> live = new LinkedHashSet<>();
        for (Class<?> mapper : mappers) {
            for (Method m : mapper.getDeclaredMethods()) {
                live.add(mapper.getSimpleName() + "#" + m.getName());
            }
        }
        List<String> stale = TENANT_PREDICATE_MATCHES_CONTEXT.stream()
                .filter(k -> !live.contains(k)).sorted().toList();
        assertThat(stale)
                .as("allowlist entries for methods that no longer exist — delete them so the "
                        + "list keeps meaning what it says")
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

    /**
     * Scanner that deliberately ignores {@code @Conditional*}.
     *
     * <p>Spring's stock provider runs a {@code ConditionEvaluator} over every
     * candidate, so a class carrying {@code @ConditionalOnProperty} /
     * {@code @ConditionalOnBean} is dropped when the condition doesn't hold —
     * and in this bare test JVM (no Spring context, no application properties)
     * essentially none of them hold. That silently shrank the corpus every rule
     * in this file runs over, by 15 classes: {@code InviteController} (a
     * <b>pre-auth</b> {@code @RestController}, gated on
     * {@code app.security.mode=oidc}), {@code UserAdminService},
     * {@code OidcJitUserService}, {@code KeycloakRealmService},
     * {@code KeycloakUserService}, {@code OutboxDispatcher},
     * {@code DynamicJobScheduler} and the rest of the Keycloak admin surface.
     * The rules didn't pass on those classes — they never saw them.
     *
     * <p>{@link #smoke_scanFindsSomething()} cannot catch this: the corpus was
     * non-empty, just incomplete. Conditions are a <em>runtime wiring</em>
     * concern; an architecture rule is about the code as written, so the right
     * answer is to evaluate none of them.
     */
    private static class FullCorpusScanner extends ClassPathScanningCandidateComponentProvider {
        private final Boolean allowInterfaces;
        /** Our own copy of the include filters — see {@link #isCandidateComponent(MetadataReader)}. */
        private final List<TypeFilter> includes = new ArrayList<>();

        /** @param allowInterfaces null → keep Spring's default candidate test. */
        FullCorpusScanner(Boolean allowInterfaces) {
            super(false);
            this.allowInterfaces = allowInterfaces;
        }

        @Override
        public void addIncludeFilter(TypeFilter includeFilter) {
            super.addIncludeFilter(includeFilter);
            includes.add(includeFilter);
        }

        /**
         * Reimplements the include-filter match and stops there.
         *
         * <p>Spring's version ends with {@code return isConditionMatch(metadataReader)},
         * and {@code isConditionMatch} is {@code private} (Spring 7), so there is
         * no narrower seam than this one. Skipping it is the entire point — see
         * the class javadoc. We keep {@code super.addIncludeFilter} in sync so
         * nothing else in the provider sees an empty filter list.
         */
        @Override
        protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
            for (TypeFilter f : includes) {
                if (f.match(metadataReader, getMetadataReaderFactory())) return true;
            }
            return false;
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            if (allowInterfaces == null) return super.isCandidateComponent(beanDefinition);
            return beanDefinition.getMetadata().isIndependent()
                    && (allowInterfaces || !beanDefinition.getMetadata().isInterface());
        }
    }

    private static List<Class<?>> findByAnnotation(Class<? extends Annotation> ann) {
        ClassPathScanningCandidateComponentProvider scanner = new FullCorpusScanner(null);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ann));
        return load(scanner.findCandidateComponents(ROOT_PACKAGE));
    }

    private static List<Class<?>> findByAssignable(Class<?> type) {
        // Default scanner skips interfaces; mappers ARE interfaces, so allow them.
        ClassPathScanningCandidateComponentProvider scanner = new FullCorpusScanner(true);
        scanner.addIncludeFilter(new AssignableTypeFilter(type));
        return load(scanner.findCandidateComponents(ROOT_PACKAGE)).stream()
                .filter(c -> !c.equals(type))   // exclude the type itself
                .toList();
    }

    private static List<Class<?>> findByPackageFragment(String fragment) {
        // Spring's scanner needs an inclusion filter to return anything — use a
        // catch-all "Object" filter and post-filter in Java.
        ClassPathScanningCandidateComponentProvider scanner = new FullCorpusScanner(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        List<Class<?>> all = load(scanner.findCandidateComponents(ROOT_PACKAGE));
        return all.stream()
                .filter(c -> c.getPackageName().contains(fragment))
                .toList();
    }

    /**
     * These rules are about <em>production</em> code. The scan sweeps the whole
     * classpath, which in this module also carries the compiled ITs, so drop
     * anything that came out of {@code target/test-classes}.
     */
    private static boolean isTestClass(BeanDefinition d) {
        String source = String.valueOf(d.getResourceDescription()).replace('\\', '/');
        return source.contains("/test-classes/");
    }

    private static List<Class<?>> load(Set<BeanDefinition> defs) {
        List<Class<?>> out = new ArrayList<>(defs.size());
        for (BeanDefinition d : defs) {
            if (isTestClass(d)) continue;
            try {
                // initialize=false on purpose. The catch-all scan in
                // findByPackageFragment loads every class under com.platform, and
                // running static initializers means running whatever they do —
                // OidcJitProvisioningIT's `static final KeycloakContainer` reaches
                // for Docker and blew up @BeforeAll with an ExceptionInInitializerError.
                // Nothing here reads a static field; reflection over members works
                // fine on an uninitialized class.
                out.add(Class.forName(d.getBeanClassName(), false,
                        ArchitectureTest.class.getClassLoader()));
            } catch (ClassNotFoundException | LinkageError e) {
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
