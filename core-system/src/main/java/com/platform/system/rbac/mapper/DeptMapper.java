package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.DeptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeptMapper extends BaseMapper<DeptEntity> {

    /** Self + all descendants — uses the {@code path} index for O(log N) prefix scan. */
    @Select("""
            SELECT id
              FROM core_rbac_dept
             WHERE mark = 1
               AND status = 1
               AND (path = #{path} OR path LIKE #{path} || '/%')
            """)
    List<String> findSubtreeIds(@Param("path") String path);

    /** Full tree for a tenant — used by tree-render endpoints and Caffeine-cached. */
    @Select("""
            SELECT *
              FROM core_rbac_dept
             WHERE mark = 1
               AND status = 1
               AND tenant_id = #{tenantId}
             ORDER BY level, sort_order, code
            """)
    List<DeptEntity> findAllForTenant(@Param("tenantId") String tenantId);
}
