package com.platform.core.infrastructure.security.rbac;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.security.DataScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Enforces the contract of {@link DataScope}: every Mapper method (or any
 * method) annotated {@code @DataScope} <b>that takes a query wrapper</b> must be
 * called with one the service has already passed through
 * {@link DataScopeHelper#apply} within the same request. Methods with no wrapper
 * parameter ({@code selectById}, {@code insert}, id-keyed hand-written
 * statements) are out of scope — see {@code takesQueryWrapper}.
 *
 * <p>Strict vs lenient is decided by the active Spring profile:
 * <ul>
 *   <li>{@code dev} / {@code test} → strict: throw 500. Developers see the
 *       missed filter immediately. (These are the two profiles
 *       {@link #isStrict()} actually checks; this repo ships
 *       {@code application{,-dev,-prod,-test}.yml} and has no {@code local}.)</li>
 *   <li>Anything else (prod) → lenient: log {@code WARN}. We don't want a
 *       single mis-wired Mapper to take down a production request, but the
 *       warning surfaces in the log pipeline and ops triage will pick it up.</li>
 * </ul>
 *
 * <p>Pointcut covers both per-method and type-level {@code @DataScope}
 * annotations. Runs at high precedence (order 5) so it fires before
 * other Mapper-level aspects.
 */
@Aspect
@Component
@Order(5)
public class DataScopeAspect {

    private static final Logger log = LoggerFactory.getLogger(DataScopeAspect.class);

    private final Environment env;

    public DataScopeAspect(Environment env) {
        this.env = env;
    }

    @Around("@annotation(com.platform.core.common.security.DataScope) " +
            "|| @within(com.platform.core.common.security.DataScope)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        if (takesQueryWrapper(pjp) && !anyArgMarked(pjp.getArgs())) {
            String sig = pjp.getSignature().toShortString();
            String msg = "@DataScope-annotated method " + sig
                    + " was invoked without a wrapper marked by DataScopeHelper.apply() — "
                    + "the service forgot to filter scope. Reject the request.";
            if (isStrict()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, msg);
            }
            log.warn(msg);
        }
        return pjp.proceed();
    }

    /**
     * Only methods that actually take a query wrapper can have forgotten to scope
     * one — {@code selectPage} / {@code selectList} / {@code selectCount} /
     * {@code update(entity, wrapper)}. Everything else on a mapper carrying a
     * TYPE-level {@code @DataScope} — {@code selectById}, {@code insert},
     * {@code updateById}, hand-written statements keyed by id — has no wrapper to
     * mark, so demanding one is not a scope check, it is an unconditional refusal.
     *
     * <p>It behaved as one. {@code dev} is the DEFAULT profile
     * ({@code active: ${SPRING_PROFILES_ACTIVE:dev}}) and {@code dev} is strict, so
     * on every developer machine three of the demo module's five endpoints —
     * {@code GET/PUT/DELETE /demo/task/&#123;id&#125;}, all of which enter
     * {@code TaskService.loadVisibleOr404} → {@code selectById(id)} — threw
     * INTERNAL_ERROR before touching the database. {@code DemoSeeder} died on its
     * first task probe for the same reason, and because {@code seed()} funnels
     * everything into one {@code log.warn("DemoSeeder: skipped — …")}, the demo
     * tasks were never planted and {@code syncUsersToKeycloak()} — the last step,
     * and the one that lets the five demo users sign in at all — never ran.
     * {@code DemoSeeder.insertTask} even worked around it locally by marking the
     * ENTITY it was about to insert, which is not a wrapper and was never scoped;
     * that workaround is gone with this fix.
     *
     * <p>The decision is on the SIGNATURE, not the runtime argument, so
     * {@code selectList(null)} — a genuine "return everything" call — is still
     * caught rather than waved through for having no wrapper instance.
     */
    private static boolean takesQueryWrapper(ProceedingJoinPoint pjp) {
        if (!(pjp.getSignature() instanceof MethodSignature ms)) return false;
        for (Class<?> t : ms.getMethod().getParameterTypes()) {
            if (Wrapper.class.isAssignableFrom(t)) return true;
        }
        return false;
    }

    private boolean anyArgMarked(Object[] args) {
        if (args == null) return false;
        for (Object a : args) {
            if (DataScopeContext.wasApplied(a)) return true;
        }
        return false;
    }

    private boolean isStrict() {
        for (String p : env.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }
}
