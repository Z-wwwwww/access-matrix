package com.platform.core.infrastructure.security.rbac;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.security.PermissionMatcher;
import com.platform.core.common.security.RequiresPermission;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Enforces {@link RequiresPermission} on controller / service methods.
 *
 * <p>Runs before {@code OpLogAspect} (Stage 4) — a denied request must not generate
 * a "success" audit row. Order is set explicitly to make this guarantee visible.
 */
@Aspect
@Component
@Order(10)
public class PermissionAspect {

    private final PermissionResolver resolver;

    public PermissionAspect(PermissionResolver resolver) {
        this.resolver = resolver;
    }

    @Before("@annotation(annotation)")
    public void check(RequiresPermission annotation) {
        String[] required = annotation.anyOf().length > 0
                ? annotation.anyOf()
                : new String[]{annotation.value()};

        if (required.length == 0 || (required.length == 1 && (required[0] == null || required[0].isBlank()))) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "@RequiresPermission must specify value() or anyOf()");
        }

        Set<String> userPerms = resolver.resolve();
        if (PermissionMatcher.matchesAny(userPerms, required)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "Missing required authority: " + String.join(",", required));
    }
}
