package com.platform.core.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a controller method requires one or more permissions.
 *
 * <p>Use {@link #value()} for the single-permission case, or {@link #anyOf()}
 * to allow any one of several permissions to satisfy the check. If both are
 * specified, {@code anyOf} wins.
 *
 * <p>Matching rules (see {@link PermissionMatcher}):
 * <ul>
 *   <li>User holding {@code *:*} passes any check.</li>
 *   <li>User holding {@code resource:*} passes any {@code resource:anyAction}.</li>
 *   <li>Otherwise the user must hold the exact permission string.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** Single required permission, e.g. {@code "user:delete"}. */
    String value() default "";

    /** Alternative: any one of these permissions satisfies the check. */
    String[] anyOf() default {};
}
