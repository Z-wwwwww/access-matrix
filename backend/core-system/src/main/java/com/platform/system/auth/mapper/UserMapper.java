package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * Login identifier lookup. The MyBatis-Plus tenant interceptor only rewrites
     * generated SQL — this is a hand-written {@code @Select}, so we filter
     * {@code tenant_id} explicitly. Caller (AuthService) reads the active
     * tenant from {@code RequestContext} (X-Tenant-Id header pre-auth).
     */
    @Select("""
            SELECT * FROM core_auth_user
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND (username = #{identifier} OR email = #{identifier} OR user_no = #{identifier})
             LIMIT 1
            """)
    UserEntity findByIdentifier(@Param("tenantId") String tenantId,
                                @Param("identifier") String identifier);

    /**
     * Look up a user by primary key AND a caller-supplied tenant. Used by
     * {@code AuthService.refresh}: the tenant is read from the refresh token
     * payload, not the request's {@code X-Tenant-Id} header — otherwise a
     * mismatched / missing header would burn a freshly-rotated refresh token.
     *
     * <p>Hand-written {@code @Select} so the MyBatis-Plus tenant interceptor
     * does NOT rewrite the {@code tenant_id} predicate from the request's
     * context.
     */
    @Select("""
            SELECT * FROM core_auth_user
             WHERE id = #{userId}
               AND tenant_id = #{tenantId}
               AND mark = 1
             LIMIT 1
            """)
    UserEntity findByIdAndTenant(@Param("userId") String userId,
                                 @Param("tenantId") String tenantId);
}
