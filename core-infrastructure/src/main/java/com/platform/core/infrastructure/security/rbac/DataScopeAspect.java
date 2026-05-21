package com.platform.core.infrastructure.security.rbac;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.security.DataScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Enforces the contract of {@link DataScope}: every Mapper method (or any
 * method) annotated {@code @DataScope} must be called with a query wrapper
 * that the service has already passed through {@link DataScopeHelper#apply}
 * within the same request.
 *
 * <p>Strict vs lenient is decided by the active Spring profile:
 * <ul>
 *   <li>{@code local} / {@code dev} / {@code test} → strict: throw 500.
 *       Developers see the missed filter immediately.</li>
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
        if (!anyArgMarked(pjp.getArgs())) {
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

    private boolean anyArgMarked(Object[] args) {
        if (args == null) return false;
        for (Object a : args) {
            if (DataScopeContext.wasApplied(a)) return true;
        }
        return false;
    }

    private boolean isStrict() {
        for (String p : env.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(p) || "dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }
}
