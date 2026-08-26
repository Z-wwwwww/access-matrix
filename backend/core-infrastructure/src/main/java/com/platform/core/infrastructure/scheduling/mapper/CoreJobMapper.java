package com.platform.core.infrastructure.scheduling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * {@code core_job} mapper. 二つの読み方をする：
 * <ul>
 *   <li>管理 API：呼び出し元の租户コンテキストでスコープされる（テナント管理者は自分の行のみ）。</li>
 *   <li>スケジューラ reconciler / 同期 guard：{@code RequestContext} を 'system' にして
 *       全租户横断に読む（{@code MybatisPlusConfig.ignoreTable} のバイパス）。</li>
 * </ul>
 */
@Mapper
public interface CoreJobMapper extends BaseMapper<CoreJobEntity> {

    /**
     * (tenant, job_code) の設定行を <b>soft-deleted (mark=0) も含めて</b> 引く。
     * 生きている行があればそちらを優先する。
     *
     * <p>ハンド SQL である理由：{@code mark} は {@code @TableLogic} なので、
     * {@code BaseMapper} の wrapper SELECT には MyBatis-Plus が必ず
     * {@code AND mark = 1} を足す。そのため {@code JobSeeder} が「孤児追従で
     * 消した行を再宣言時に復活させる」と謳っていた分岐には決して入れず、代わりに
     * 既定 cron の新規行が挿入されて管理者の設定（cron / enabled / concurrent /
     * max_run_seconds / name）が黙って失われていた。ハンド SQL は logic-delete
     * ハンドラに書き換えられないので、mark=0 の行も見える。
     *
     * <p>租户拦截器は呼び出し元が system 租户のとき全テーブルを ignore するので、
     * {@code JobSeeder} の system コンテキスト下では追加の述語も入らない。
     */
    @Select("""
            SELECT * FROM core_job
             WHERE tenant_id = #{tenantId}
               AND job_code  = #{jobCode}
             ORDER BY mark DESC
             LIMIT 1
            """)
    CoreJobEntity findAnyByCode(@Param("tenantId") String tenantId, @Param("jobCode") String jobCode);

    /**
     * soft-deleted 行を復活させる。{@code UpdateWrapper} では出来ない：
     * そちらにも同じ {@code AND mark = 1} ガードが付くため mark=0 の行には当たらない。
     *
     * @return 更新行数（1=復活した / 0=既に生きている or 行なし）
     */
    @Update("UPDATE core_job SET mark = 1, update_time = #{now} WHERE id = #{id} AND mark = 0")
    int revive(@Param("id") String id, @Param("now") OffsetDateTime now);
}
