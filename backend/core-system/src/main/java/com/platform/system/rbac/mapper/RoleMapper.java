package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    /** Role IDs (only enabled roles) bound to a given user. */
    @Select("""
            SELECT r.id
              FROM core_rbac_role r
              JOIN core_rbac_user_role ur
                ON ur.role_id = r.id AND ur.mark = 1
             WHERE r.mark = 1
               AND r.status = 1
               AND ur.user_id = #{userId}
            """)
    List<String> findRoleIdsByUserId(@Param("userId") String userId);

    /** Full role records (only enabled) bound to a given user. */
    @Select("""
            SELECT r.*
              FROM core_rbac_role r
              JOIN core_rbac_user_role ur
                ON ur.role_id = r.id AND ur.mark = 1
             WHERE r.mark = 1
               AND r.status = 1
               AND ur.user_id = #{userId}
            """)
    List<RoleEntity> findRolesByUserId(@Param("userId") String userId);
}
