package com.platform.core.bootstrap.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.core.bootstrap.auth.entity.LoginLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogEntity> {
}
