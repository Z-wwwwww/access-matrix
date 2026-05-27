package com.platform.system.platform.mapper;

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
     * <p>Hand-written {@code @Select} so the MyBatis-Plus tenant interceptor
     * doesn't add {@code WHERE tenant_id = ?} via the request context.
     * The {@code core_tenant} table is owned by the system tenant
     * (every row has {@code tenant_id='system'}), and callers reaching
     * this mapper are platform-ops users who already bypass scoping
     * — but pinning the lookup explicitly keeps it correct even if the
     * caller's context is wrong.
     */
    @Select("""
            SELECT * FROM core_tenant
             WHERE mark = 1
               AND tenant_code = #{tenantCode}
             LIMIT 1
            """)
    TenantEntity findActiveByCode(@Param("tenantCode") String tenantCode);
}
