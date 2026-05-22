package com.platform.business.demo.security;

import com.platform.core.common.security.PermissionCode;
import org.springframework.stereotype.Component;

/**
 * business-demo モジュールの権限コード常量。詳細は {@code SystemPermissions} の Javadoc を参照。
 */
@Component
public final class DemoPermissions {

    public static final String TASK_READ   = "task:read";
    public static final String TASK_CREATE = "task:create";
    public static final String TASK_UPDATE = "task:update";
    public static final String TASK_DELETE = "task:delete";

    static {
        PermissionCode.registerAll(DemoPermissions.class, "demo");
    }

    DemoPermissions() {}
}
