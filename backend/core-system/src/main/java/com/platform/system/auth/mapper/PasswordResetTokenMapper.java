package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.auth.entity.PasswordResetTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenEntity> {

    /**
     * Look up an outstanding reset token by its hashed value. Returns null
     * for not-found / soft-deleted / consumed rows. Expiry is checked in
     * Java for a cleaner "not-found vs expired vs used" distinction in
     * controller responses.
     *
     * <p>Hand-written {@code @Select} — pre-auth context (the user clicks
     * the email link BEFORE a session exists), so {@code RequestContext}
     * has no tenant; token_hash is globally unique by V24's index and is
     * itself the proof of identity.
     */
    @Select("""
            SELECT * FROM core_password_reset_token
             WHERE mark = 1
               AND token_hash = #{tokenHash}
               AND used_at IS NULL
             LIMIT 1
            """)
    PasswordResetTokenEntity findActiveByTokenHash(@Param("tokenHash") String tokenHash);
}
