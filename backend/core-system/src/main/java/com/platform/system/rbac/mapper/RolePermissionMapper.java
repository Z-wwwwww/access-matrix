package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.RolePermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {

    /**
     * Permission IDs bound to a role <b>where the permission itself is still live</b>,
     * tenant-scoped. Filters out dangling links to soft-deleted permissions so the
     * admin UI never picks up "ghost" selections that would later trip {@code assertAllExist}.
     */
    @Select("""
            SELECT rp.permission_id
              FROM core_rbac_role_permission rp
              JOIN core_rbac_permission p
                ON p.id = rp.permission_id AND p.mark = 1 AND p.tenant_id = #{tenantId}
             WHERE rp.role_id = #{roleId}
               AND rp.mark = 1
               AND rp.tenant_id = #{tenantId}
            """)
    List<String> findActivePermissionIdsByRoleId(@Param("roleId") String roleId,
                                                 @Param("tenantId") String tenantId);
}
