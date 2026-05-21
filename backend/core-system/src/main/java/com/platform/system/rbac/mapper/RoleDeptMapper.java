package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.RoleDeptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleDeptMapper extends BaseMapper<RoleDeptEntity> {

    /** Department IDs explicitly granted to a role (CUSTOM data scope). */
    @Select("""
            SELECT dept_id
              FROM core_rbac_role_dept
             WHERE mark = 1
               AND role_id = #{roleId}
            """)
    List<String> findDeptIdsByRoleId(@Param("roleId") String roleId);
}
