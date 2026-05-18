package com.platform.core.bootstrap.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.core.bootstrap.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("""
            SELECT * FROM pms_auth_user
             WHERE mark = 1
               AND (username = #{identifier} OR email = #{identifier} OR user_no = #{identifier})
             LIMIT 1
            """)
    UserEntity findByIdentifier(@Param("identifier") String identifier);
}
