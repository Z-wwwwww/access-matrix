package com.platform.core.infrastructure.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.core.infrastructure.event.entity.DomainEventEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Picked up by {@code @MapperScan("com.platform.**.mapper")} in MybatisPlusConfig.
 *
 * <p>Business code never uses this directly — it goes through {@code EventPublisher}.
 * The future outbox dispatcher will read pending rows here via hand-written,
 * cross-tenant SQL (the tenant interceptor must be bypassed for the background
 * scan; see the V36 migration header).
 */
@Mapper
public interface DomainEventMapper extends BaseMapper<DomainEventEntity> {
}
