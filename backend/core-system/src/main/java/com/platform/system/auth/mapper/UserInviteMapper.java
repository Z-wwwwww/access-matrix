package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.auth.entity.UserInviteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * Tenant interceptor: the four token-keyed statements below carry
 * {@code @InterceptorIgnore(tenantLine = "true")}, and it is REQUIRED, not
 * decorative. MyBatis-Plus rewrites ALL SQL — including hand-written
 * {@code @Select} / {@code @Update} — so without it each statement gets
 * {@code AND tenant_id = <RequestContext.tenantId()>} appended. These run
 * PRE-AUTH, where the context tenant comes from the {@code X-Tenant-Id} header
 * that the SPA derives from the SUBDOMAIN — but the email link is built from the
 * single global {@code app.mail.base-url}, so for a first-time recipient (nothing
 * in localStorage, apex/reserved host) it resolves to the {@code demo} fallback.
 * Verified against the real DB: a {@code sozonext} token is found by the intended
 * SQL and by 0 rows once {@code AND tenant_id = 'demo'} is appended, and the claim
 * UPDATE likewise affects 0 rows — which callers must read as "already used". Net
 * effect before these annotations: every invite / password-reset link outside the
 * {@code demo} tenant reported "invalid or expired".
 *
 * <p>Safe because {@code token_hash} is globally unique (partial unique index on
 * {@code mark = 1}) and the consuming service takes tenant + user FROM the row it
 * just claimed, never from the request. Same precedent and reasoning as
 * {@code TenantMapper.findActiveByCode}.
 */
@Mapper
public interface UserInviteMapper extends BaseMapper<UserInviteEntity> {

    // See the class javadoc "Tenant interceptor" note — this annotation is REQUIRED.
    @InterceptorIgnore(tenantLine = "true")
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
    // See the class javadoc "Tenant interceptor" note — this annotation is REQUIRED.
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE core_user_invite
               SET used_at = #{now}, update_user = 'system'
             WHERE id = #{id}
               AND used_at IS NULL
               AND mark = 1
            """)
    int markUsed(@Param("id") String id, @Param("now") OffsetDateTime now);
}
