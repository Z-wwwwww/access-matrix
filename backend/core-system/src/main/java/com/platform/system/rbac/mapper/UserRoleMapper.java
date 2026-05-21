package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.UserRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    /** Users currently linked to a role (used for permission-cache invalidation). */
    @Select("""
            SELECT DISTINCT user_id
              FROM core_rbac_user_role
             WHERE mark = 1
               AND role_id = #{roleId}
            """)
    List<String> findUserIdsByRoleId(@Param("roleId") String roleId);

    /**
     * Count distinct ACTIVE (user mark=1 + status=1) holders of a role.
     * Used by the "last super admin" guard so we never let the platform
     * end up with zero usable super admins.
     */
    @Select("""
            SELECT COUNT(DISTINCT ur.user_id)
              FROM core_rbac_user_role ur
              JOIN core_auth_user u
                ON u.id = ur.user_id AND u.mark = 1 AND u.status = 1
             WHERE ur.mark = 1
               AND ur.role_id = #{roleId}
            """)
    Long countActiveHoldersByRoleId(@Param("roleId") String roleId);

    /** Cheap existence probe: returns 1 if the user has a live link to the role, null otherwise. */
    @Select("""
            SELECT 1
              FROM core_rbac_user_role
             WHERE mark = 1
               AND user_id = #{userId}
               AND role_id = #{roleId}
             LIMIT 1
            """)
    Integer existsActiveLink(@Param("userId") String userId, @Param("roleId") String roleId);
}
