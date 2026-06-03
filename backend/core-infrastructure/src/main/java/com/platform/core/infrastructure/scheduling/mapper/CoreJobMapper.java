package com.platform.core.infrastructure.scheduling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.core.infrastructure.scheduling.entity.CoreJobEntity;
import org.apache.ibatis.annotations.Mapper;

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
}
