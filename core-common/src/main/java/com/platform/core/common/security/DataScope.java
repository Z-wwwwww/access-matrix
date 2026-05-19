package com.platform.core.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code Mapper} (or service) method as participating in data-scope
 * filtering. The annotation itself is informational — it documents intent
 * and lets ArchUnit / static checks enforce that every annotated point has
 * a matching {@code DataScopeHelper.apply(...)} call in the corresponding
 * service.
 *
 * <p>The actual SQL injection happens in the service layer via
 * {@code DataScopeHelper.apply(wrapper, decision, deptColumn, creatorColumn)} —
 * deliberately explicit so the filter is visible at the call site (no
 * hidden interceptor surprises).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** Optional descriptive label, e.g. {@code "customer.list"}. */
    String value() default "";

    /** Column carrying the department id, default {@code dept_id}. */
    String deptColumn() default "dept_id";

    /** Column carrying the creator user id, default {@code create_user}. */
    String creatorColumn() default "create_user";
}
