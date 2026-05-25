package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {

    /**
     * Permission codes a user can exercise — walks user→role→permission, distinct,
     * respecting all three tables' mark=1 and the role's status=1.
     *
     * <p>Tenant scoping: every table in the JOIN is filtered by {@code tenant_id}
     * (defense-in-depth). The {@code TenantLineInnerInterceptor} would already
     * inject this in dev/prod, but we duplicate it here so the SQL is correct
     * even when the interceptor is disabled (local profile), and so the intent
     * is self-evident on read.
     */
    @Select("""
            SELECT DISTINCT p.code
              FROM core_rbac_permission p
              JOIN core_rbac_role_permission rp
                ON rp.permission_id = p.id AND rp.mark = 1 AND rp.tenant_id = #{tenantId}
              JOIN core_rbac_role r
                ON r.id = rp.role_id AND r.mark = 1 AND r.status = 1 AND r.tenant_id = #{tenantId}
              JOIN core_rbac_user_role ur
                ON ur.role_id = r.id AND ur.mark = 1 AND ur.tenant_id = #{tenantId}
             WHERE p.mark = 1
               AND p.tenant_id = #{tenantId}
               AND ur.user_id = #{userId}
            """)
    List<String> findPermissionCodesByUserId(@Param("userId") String userId,
                                             @Param("tenantId") String tenantId);
}
