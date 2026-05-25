package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.RoleDeptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleDeptMapper extends BaseMapper<RoleDeptEntity> {

    /**
     * Department IDs explicitly granted to a role (CUSTOM data scope).
     * Does NOT filter by dept.mark — preserves legacy behaviour for the data-scope
     * runtime path (a soft-deleted dept stays in the scope until explicitly removed).
     */
    @Select("""
            SELECT dept_id
              FROM core_rbac_role_dept
             WHERE mark = 1
               AND role_id = #{roleId}
            """)
    List<String> findDeptIdsByRoleId(@Param("roleId") String roleId);

    /**
     * Department IDs bound to a role <b>where the dept itself is still live</b>.
     * Used by the admin UI's role-edit drawer — same intent as the JOIN'd helpers
     * on {@code RolePermissionMapper} / {@code RoleMenuMapper}: never hand the UI
     * IDs that would later fail {@code assertAllExist}.
     */
    @Select("""
            SELECT rd.dept_id
              FROM core_rbac_role_dept rd
              JOIN core_rbac_dept d ON d.id = rd.dept_id AND d.mark = 1
             WHERE rd.role_id = #{roleId}
               AND rd.mark = 1
            """)
    List<String> findActiveDeptIdsByRoleId(@Param("roleId") String roleId);
}
