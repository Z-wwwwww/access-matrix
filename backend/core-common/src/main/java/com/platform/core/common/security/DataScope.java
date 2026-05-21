package com.platform.core.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code Mapper} (or service) method as participating in data-scope
 * filtering.
 *
 * <p>Enforcement at runtime is wired through {@code DataScopeAspect}: any
 * annotated method invocation whose query-wrapper argument has not been
 * passed through {@code DataScopeHelper.apply(...)} in the current request
 * is rejected (strict profiles: {@code local} / {@code dev} / {@code test})
 * or logged as {@code WARN} (prod). Service authors thus get an immediate
 * failure when they forget the scope filter — no silent "all rows" leak.
 *
 * <p>The actual SQL injection happens in the service layer via
 * {@code DataScopeHelper.apply(wrapper, decision, deptColumn, creatorColumn)} —
 * deliberately explicit so the filter is visible at the call site (no
 * hidden interceptor surprises). The aspect only verifies that the call
 * happened; it never silently adds a filter.
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
