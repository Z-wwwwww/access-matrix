package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.auth.entity.UserInviteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

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

    /**
     * Atomically claim (consume) an invite: flip {@code used_at} from NULL → now
     * for a still-active row. Returns the number of rows updated — exactly 1 for
     * the first caller, 0 for any later caller (already used) or a lost race.
     * This is the single-use guarantee: callers MUST treat a 0 result as
     * "already used / invalid" and refuse the operation. Hand-written so the
     * UPDATE definitely executes and its affected-row count is observable (a
     * prior SELECT-then-UpdateWrapper approach silently allowed re-use).
     */
    @Update("""
            UPDATE core_user_invite
               SET used_at = #{now}, update_user = 'system'
             WHERE id = #{id}
               AND used_at IS NULL
               AND mark = 1
            """)
    int markUsed(@Param("id") String id, @Param("now") LocalDateTime now);
}
