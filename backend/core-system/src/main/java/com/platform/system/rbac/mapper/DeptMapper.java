package com.platform.system.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.rbac.entity.DeptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeptMapper extends BaseMapper<DeptEntity> {

    /** Self + all descendants, tenant-scoped — uses the {@code path} index for O(log N) prefix scan. */
    @Select("""
            SELECT id
              FROM core_rbac_dept
             WHERE mark = 1
               AND status = 1
               AND tenant_id = #{tenantId}
               AND (path = #{path} OR path LIKE #{path} || '/%')
            """)
    List<String> findSubtreeIds(@Param("path") String path,
                                @Param("tenantId") String tenantId);

    /**
     * Self + all descendants regardless of {@code status} — the STRUCTURAL view
     * of the subtree. Use this whenever the answer feeds a tree mutation
     * (cycle detection, cascade delete); use {@link #findSubtreeIds} only when
     * the question is "what data does this scope see", where excluding a disabled
     * dept is the intended semantic.
     *
     * <p>Mixing the two up produced two real defects, both verified against the
     * DB: force-deleting a dept whose child was disabled left the child alive
     * with {@code parent_id} pointing at a soft-deleted row, and the reparent
     * cycle check failed to see a disabled descendant, so setting a dept's
     * parent to it produced a genuine {@code parent_id} cycle (A&rarr;B&rarr;A)
     * plus a {@code path} containing the dept's own id.
     */
    @Select("""
            SELECT id
              FROM core_rbac_dept
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND (path = #{path} OR path LIKE #{path} || '/%')
            """)
    List<String> findSubtreeIdsAnyStatus(@Param("path") String path,
                                         @Param("tenantId") String tenantId);

    /**
     * Re-root every STRICT DESCENDANT of a moved department: swap the old ancestor
     * prefix in {@code path} for the new one and shift {@code level} by the same
     * delta the moved node itself shifted.
     *
     * <p>Required because {@code path} and {@code level} are DERIVED from the parent
     * chain, and a reparent only rewrote them on the moved node. That left the tree
     * self-inconsistent: {@code parent_id} (what the admin UI renders) stayed correct
     * while {@code path} — which is what the data-scope resolution actually keys on —
     * still described the OLD position. Verified against the DB: moving a department
     * with a child made {@code DEPT_AND_SUB} for the moved node return only itself
     * (its manager lost the child's data), while the child stayed attached to the old
     * ancestor's subtree. The code acknowledged the gap and deferred it to a
     * "rebuild paths" admin command that does not exist.
     *
     * <p>Matched on {@code mark} only, deliberately — a DISABLED descendant must move
     * with its parent, for the same reason {@code findSubtreeIdsAnyStatus} exists.
     * {@code substring(path from N)} keeps each descendant's own tail intact, so a
     * whole multi-level subtree is re-rooted in one statement.
     */
    @Update("""
            UPDATE core_rbac_dept
               SET path  = #{newPath} || substring(path from #{oldPathLen} + 1),
                   level = level + #{levelDelta},
                   update_user = 'system'
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND path LIKE #{oldPath} || '/%'
            """)
    int reRootDescendants(@Param("oldPath") String oldPath,
                          @Param("oldPathLen") int oldPathLen,
                          @Param("newPath") String newPath,
                          @Param("levelDelta") int levelDelta,
                          @Param("tenantId") String tenantId);

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
