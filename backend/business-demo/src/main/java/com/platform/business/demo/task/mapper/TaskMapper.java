package com.platform.business.demo.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.core.common.security.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * Task mapper. Type-level {@link DataScope} marks every method as requiring
 * a {@code DataScopeHelper.apply(...)} call upstream — the runtime aspect
 * fails fast (dev) / WARNs (prod) if a caller forgets to scope the query.
 *
 * <p>SELECT methods we expect the service to scope: {@code selectPage} /
 * {@code selectList} / {@code selectCount}. Single-row {@code selectById}
 * already hits the tenant interceptor for tenant isolation; it does not
 * carry dept scoping by design (the service guards reads of foreign
 * tasks with a permission check).
 */
@Mapper
@DataScope(deptColumn = "dept_id", creatorColumn = "create_user")
public interface TaskMapper extends BaseMapper<TaskEntity> {
}
