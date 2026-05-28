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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
            "PasswordResetTokenEntity"   // same single-use token shape
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
            "ScopeMeController"          // /scope/me — same
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
