package com.platform.business.demo.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.core.common.security.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

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

    /**
     * The row's {@code mark} for a given id, or null when no row exists at all —
     * i.e. an existence probe that can tell "soft-deleted" apart from "absent".
     *
     * <p>{@code selectById} cannot: {@code mark} is {@code @TableLogic}, so
     * MyBatis-Plus appends {@code AND mark = 1} and a soft-deleted row comes back
     * as null — indistinguishable from a row that was never seeded. The seeded
     * demo rows carry FIXED ids, so acting on that null means re-inserting an id
     * the table still holds, and the primary key rejects it. Hand-written SQL is
     * not rewritten by the logic-delete handler.
     *
     * <p>No tenant predicate: the id is a globally unique primary key, and the
     * MyBatis tenant interceptor still scopes the statement to the caller's
     * tenant (the seeder pins {@code demo}).
     */
    @Select("SELECT mark FROM demo_task WHERE id = #{id}")
    Integer findMarkById(@Param("id") String id);

    /**
     * Bring a soft-deleted row back. An {@code UpdateWrapper} cannot: it carries
     * the same {@code AND mark = 1} guard, so it never matches a {@code mark = 0}
     * row.
     *
     * @return rows updated — 1 when a soft-deleted row was revived, 0 otherwise
     */
    @Update("UPDATE demo_task SET mark = 1, update_time = #{now} WHERE id = #{id} AND mark = 0")
    int reviveById(@Param("id") String id, @Param("now") OffsetDateTime now);
}
