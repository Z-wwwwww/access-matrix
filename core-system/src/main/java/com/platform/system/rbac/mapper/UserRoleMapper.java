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
}
