package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.UserRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    /** Users currently linked to a role (used for permission-cache invalidation), tenant-scoped. */
    @Select("""
            SELECT DISTINCT user_id
              FROM core_rbac_user_role
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND role_id = #{roleId}
            """)
    List<String> findUserIdsByRoleId(@Param("roleId") String roleId,
                                     @Param("tenantId") String tenantId);

    /**
     * Count distinct ACTIVE (user mark=1 + status=1) holders of a role, tenant-scoped.
     * Used by the "last super admin" guard so we never let the platform
     * end up with zero usable super admins.
     */
    @Select("""
            SELECT COUNT(DISTINCT ur.user_id)
              FROM core_rbac_user_role ur
              JOIN core_auth_user u
                ON u.id = ur.user_id AND u.mark = 1 AND u.status = 1 AND u.tenant_id = #{tenantId}
             WHERE ur.mark = 1
               AND ur.tenant_id = #{tenantId}
               AND ur.role_id = #{roleId}
            """)
    Long countActiveHoldersByRoleId(@Param("roleId") String roleId,
                                    @Param("tenantId") String tenantId);

    /**
     * Role IDs bound to a user <b>where the role itself is still live and in this
     * tenant</b>. Mirrors {@code RolePermissionMapper.findActivePermissionIdsByRoleId}:
     * a plain {@code selectList(user_id, mark=1)} also returns dangling links —
     * to soft-deleted roles, or (since the FK only checks {@code core_rbac_role(id)},
     * not the tenant) to another tenant's role — which surface as "ghost selections"
     * in the role-assignment dialog: a checked role that isn't in the tenant's role
     * list at all.
     */
    @Select("""
            SELECT ur.role_id
              FROM core_rbac_user_role ur
              JOIN core_rbac_role r
                ON r.id = ur.role_id AND r.mark = 1 AND r.tenant_id = #{tenantId}
             WHERE ur.user_id = #{userId}
               AND ur.mark = 1
               AND ur.tenant_id = #{tenantId}
            """)
    List<String> findActiveRoleIdsByUserId(@Param("userId") String userId,
                                           @Param("tenantId") String tenantId);

    /**
     * How many of {@code roleIds} are live roles of {@code tenantId}. The caller
     * compares against the deduped input size to reject unknown / soft-deleted /
     * cross-tenant ids before inserting any link (see
     * {@code UserAdminService.assertRolesExist}).
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM core_rbac_role
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND id IN
               <foreach item="id" collection="roleIds" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    Long countLiveRoles(@Param("roleIds") java.util.Collection<String> roleIds,
                        @Param("tenantId") String tenantId);

    /** Cheap existence probe: returns 1 if the user has a live link to the role within the tenant. */
    @Select("""
            SELECT 1
              FROM core_rbac_user_role
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND user_id = #{userId}
               AND role_id = #{roleId}
             LIMIT 1
            """)
    Integer existsActiveLink(@Param("userId") String userId,
                             @Param("roleId") String roleId,
                             @Param("tenantId") String tenantId);
}
