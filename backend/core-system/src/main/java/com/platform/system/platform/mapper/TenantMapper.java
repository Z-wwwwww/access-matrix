package com.platform.system.platform.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.platform.entity.TenantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {

    /**
     * Look up a tenant by its code. Returns null when no active row matches.
     *
     * <p>{@code core_tenant} is a platform-global registry: every row has
     * {@code tenant_id='system'} (the column means "owned by platform-ops",
     * NOT "scoped to this tenant"). The MyBatis-Plus tenant interceptor
     * rewrites ALL SQL — including hand-written {@code @Select} — so without
     * {@code @InterceptorIgnore} it injects {@code AND tenant_id = ?} from the
     * request context. That breaks the pre-auth password / break-glass login
     * path ({@code AuthService.assertTenantActive}): for a business tenant the
     * context is e.g. {@code 'acme'}, the predicate becomes
     * {@code tenant_id='acme'}, no registry row matches (they're all
     * {@code 'system'}), and a perfectly active tenant is misreported as
     * suspended. Platform-ops callers already bypass scoping via
     * {@code MybatisPlusConfig.ignoreTable}; this annotation makes the lookup
     * correct for the pre-auth path too. The table stays OUT of
     * {@code TENANT_EXCLUDED_TABLES} (it has a real {@code tenant_id} column,
     * so {@code TenantSchemaGuard} would flag a global exclusion as a wasted
     * one) — the ignore is scoped to exactly this cross-registry query.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM core_tenant
             WHERE mark = 1
               AND tenant_code = #{tenantCode}
             LIMIT 1
            """)
    TenantEntity findActiveByCode(@Param("tenantCode") String tenantCode);
}
