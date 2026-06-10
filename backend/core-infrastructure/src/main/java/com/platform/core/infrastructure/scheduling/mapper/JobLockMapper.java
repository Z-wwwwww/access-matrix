package com.platform.core.infrastructure.scheduling.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

/**
 * 分布式ロック ({@code core_job_lock}) のハンドSQL mapper。グローバルテーブルなので
 * 租户拦截器の対象外（{@code MybatisPlusConfig.TENANT_EXCLUDED_TABLES} に登録済み）。
 */
@Mapper
public interface JobLockMapper {

    /**
     * ロック取得（単一原子文）。未取得なら INSERT、既存だが {@code lock_until} が
     * 過ぎていれば奪取。戻り値 1 = 取得成功、0 = 他ノードが生きたロックを保持中。
     *
     * <p>{@code ON CONFLICT ... DO UPDATE ... WHERE lock_until <= now} は Postgres の
     * 標準的な「期限切れロックだけ奪う」イディオム。ON CONFLICT が取る行ロックの下で
     * 評価されるので競合安全。
     */
    @Insert("""
            INSERT INTO core_job_lock (lock_name, locked_at, lock_until, locked_by)
            VALUES (#{lockName}, #{now}, #{lockUntil}, #{nodeId})
            ON CONFLICT (lock_name) DO UPDATE
               SET locked_at  = EXCLUDED.locked_at,
                   lock_until = EXCLUDED.lock_until,
                   locked_by  = EXCLUDED.locked_by
             WHERE core_job_lock.lock_until <= #{now}
            """)
    int tryAcquire(@Param("lockName") String lockName,
                   @Param("now") OffsetDateTime now,
                   @Param("lockUntil") OffsetDateTime lockUntil,
                   @Param("nodeId") String nodeId);

    /**
     * ロック解放。{@code locked_by} で守る — 期限切れで他ノードに奪われた後に
     * 自分が誤って解放しないため。
     */
    @Delete("DELETE FROM core_job_lock WHERE lock_name = #{lockName} AND locked_by = #{nodeId}")
    int release(@Param("lockName") String lockName, @Param("nodeId") String nodeId);
}
