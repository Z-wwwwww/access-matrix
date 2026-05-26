package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.auth.entity.UserInviteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserInviteMapper extends BaseMapper<UserInviteEntity> {

    /**
     * Look up an outstanding invite by its hashed token. Returns null when:
     *   - no row with this token_hash exists, OR
     *   - the row is soft-deleted (mark=0), OR
     *   - the row was already consumed (used_at IS NOT NULL).
     *
     * <p>Expiry check is intentionally done in Java (so a clean
     * "expired vs not-found vs already-used" distinction can be surfaced
     * in the API response — this query just filters out the trivially-dead
     * cases).
     *
     * <p>Hand-written @Select — pre-auth (the user clicks the email link
     * BEFORE having a session), so we trust the token to scope and don't
     * have a tenant in {@code RequestContext}. token_hash is globally
     * unique by V22's index.
     */
    @Select("""
            SELECT * FROM core_user_invite
             WHERE mark = 1
               AND token_hash = #{tokenHash}
               AND used_at IS NULL
             LIMIT 1
            """)
    UserInviteEntity findActiveByTokenHash(@Param("tokenHash") String tokenHash);
}
