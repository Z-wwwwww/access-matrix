package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.auth.entity.PasswordResetTokenEntity;
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
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenEntity> {

    // See the class javadoc "Tenant interceptor" note — this annotation is REQUIRED.
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM core_password_reset_token
             WHERE mark = 1
               AND token_hash = #{tokenHash}
               AND used_at IS NULL
             LIMIT 1
            """)
    PasswordResetTokenEntity findActiveByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Atomically claim (consume) a reset token: flip {@code used_at} from NULL → now
     * for a still-active row. Returns the number of rows updated — exactly 1 for the
     * first caller, 0 for any later caller (already used) or a lost race. This is the
     * single-use guarantee: callers MUST treat a 0 result as "already used / invalid"
     * and refuse the operation.
     *
     * <p>Hand-written (mirrors {@link UserInviteMapper#markUsed}) so the UPDATE
     * definitely executes and its affected-row count is observable — the previous
     * SELECT-then-UpdateWrapper version discarded that count, so two concurrent
     * POSTs of the same link both passed and each wrote a password.
     */
    // See the class javadoc "Tenant interceptor" note — this annotation is REQUIRED.
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE core_password_reset_token
               SET used_at = #{now}, update_user = 'system'
             WHERE id = #{id}
               AND used_at IS NULL
               AND mark = 1
            """)
    int markUsed(@Param("id") String id, @Param("now") OffsetDateTime now);

    /**
     * Has this user ever COMPLETED the SSO&rarr;password reverse migration?
     *
     * <p>Rows in this table are minted by exactly one caller —
     * {@code SsoToPasswordMigrationService} — so a consumed row is an unambiguous
     * "this account was deliberately moved off SSO onto a local password". Used by
     * {@code OidcJitUserService} to refuse re-attaching such an account to Keycloak:
     * {@code PasswordResetController} clears {@code keycloak_id} and disables the
     * Keycloak user, but that disable is BEST-EFFORT (a KC hiccup must not block the
     * user's own recovery), so a surviving — or later re-enabled — KC account can
     * still complete an SSO login. Without this check the JIT legacy-bind branch then
     * matches the user by username, re-writes {@code keycloak_id} AND nulls the
     * {@code password_hash} they just set, silently undoing a flow documented as
     * irreversible and leaving them unable to log in either way (the reset token is
     * single-use and already spent).
     *
     * <p>Explicit tenant param + hand-written SQL for the same reason as
     * {@code UserMapper.countDeletedByKeycloakIdAndTenant}.
     */
    @Select("""
            SELECT COUNT(*) FROM core_password_reset_token
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND user_id = #{userId}
               AND used_at IS NOT NULL
            """)
    long countConsumedByUser(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
