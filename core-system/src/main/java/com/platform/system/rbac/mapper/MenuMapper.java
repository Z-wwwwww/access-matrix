package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.MenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapper<MenuEntity> {

    /** All visible (mark=1, status=1) menus across the tenant — used by super admin. */
    @Select("""
            SELECT *
              FROM core_rbac_menu
             WHERE mark = 1
               AND status = 1
             ORDER BY sort_order, code
            """)
    List<MenuEntity> findAllVisible();

    /** Distinct menus a user can see (walks user→role→role_menu→menu). Caller folds in parents. */
    @Select("""
            SELECT DISTINCT m.*
              FROM core_rbac_menu m
              JOIN core_rbac_role_menu rm
                ON rm.menu_id = m.id AND rm.mark = 1
              JOIN core_rbac_role r
                ON r.id = rm.role_id AND r.mark = 1 AND r.status = 1
              JOIN core_rbac_user_role ur
                ON ur.role_id = r.id AND ur.mark = 1
             WHERE m.mark = 1
               AND m.status = 1
               AND ur.user_id = #{userId}
             ORDER BY sort_order, code
            """)
    List<MenuEntity> findMenusByUserId(@Param("userId") String userId);

    /** Hydrate parent chain for the supplied id set — guarantees the tree never has dangling children. */
    @Select("""
            <script>
            SELECT *
              FROM core_rbac_menu
             WHERE mark = 1
               AND status = 1
               AND id IN
            <foreach item='id' collection='ids' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
    List<MenuEntity> findByIdIn(@Param("ids") List<String> ids);
}
