package com.platform.core.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller / service method whose invocation should be persisted
 * to {@code core_oplog}. The aspect captures:
 * <ul>
 *   <li>module / action / target — supplied by the annotation.</li>
 *   <li>caller userId / username — from JWT (or RequestContext).</li>
 *   <li>HTTP method / URI / IP / User-Agent — from current request.</li>
 *   <li>request body — JSON-serialised, truncated to 4KB, password
 *       fields auto-masked to {@code ***}.</li>
 *   <li>success / errorMsg / cost_ms — from the @Around invocation.</li>
 * </ul>
 *
 * <p>Write is asynchronous so the audit row never blocks the business
 * response.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** Owning module: {@code system}, {@code pms}, {@code iot}... */
    String module();

    /** Verb-ish identifier, e.g. {@code "role.create"}, {@code "user.assignRoles"}. */
    String action();

    /** Optional target-entity type, e.g. {@code "role"}. */
    String targetType() default "";
}
