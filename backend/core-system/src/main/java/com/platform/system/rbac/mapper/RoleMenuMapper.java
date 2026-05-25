package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.RoleMenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenuEntity> {

    /**
     * Menu IDs bound to a role <b>where the menu itself is still live</b>, tenant-scoped.
     * Mirrors {@code RolePermissionMapper.findActivePermissionIdsByRoleId} —
     * shields the admin UI from ghost selections pointing to soft-deleted menus.
     */
    @Select("""
            SELECT rm.menu_id
              FROM core_rbac_role_menu rm
              JOIN core_rbac_menu m
                ON m.id = rm.menu_id AND m.mark = 1 AND m.tenant_id = #{tenantId}
             WHERE rm.role_id = #{roleId}
               AND rm.mark = 1
               AND rm.tenant_id = #{tenantId}
            """)
    List<String> findActiveMenuIdsByRoleId(@Param("roleId") String roleId,
                                           @Param("tenantId") String tenantId);
}
