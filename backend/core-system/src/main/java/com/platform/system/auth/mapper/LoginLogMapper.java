package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.auth.entity.LoginLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogEntity> {
}
