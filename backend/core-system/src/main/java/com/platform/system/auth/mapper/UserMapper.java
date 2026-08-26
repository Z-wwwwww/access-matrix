package com.platform.system.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * Login identifier lookup. {@code tenant_id} is filtered explicitly so the SQL
     * reads correctly on its own; the caller (AuthService) passes the active tenant
     * from {@code RequestContext} (X-Tenant-Id header pre-auth).
     *
     * <p>Note that the explicit predicate is NOT what keeps the interceptor out:
     * MyBatis-Plus rewrites ALL SQL, hand-written {@code @Select} included, so this
     * statement also gets {@code AND tenant_id = <RequestContext.tenantId()>}
     * appended. It is harmless here only because both predicates carry the same
     * value. Any lookup that must run under a tenant OTHER than the request's needs
     * {@code @InterceptorIgnore(tenantLine = "true")} or a re-established context —
     * see {@link #findByIdAndTenant} and {@code PasswordResetTokenMapper}.
     */
    @Select("""
            SELECT * FROM core_auth_user
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND (username = #{identifier} OR email = #{identifier} OR user_no = #{identifier})
             LIMIT 1
            """)
    UserEntity findByIdentifier(@Param("tenantId") String tenantId,
                                @Param("identifier") String identifier);

    /**
     * Look up a user by primary key AND a caller-supplied tenant. Used by
     * {@code AuthService.refresh}: the tenant is read from the refresh token
     * payload, not the request's {@code X-Tenant-Id} header — otherwise a
     * mismatched / missing header would burn a freshly-rotated refresh token.
     *
     * <p><b>Hand-written {@code @Select} does NOT keep the tenant interceptor out.</b>
     * MyBatis-Plus rewrites every statement it can parse, so this one also receives
     * {@code AND tenant_id = <RequestContext.tenantId()>}. This is the one lookup in
     * the file whose tenant argument deliberately differs from the request's, so both
     * of its callers re-establish {@code RequestContext} on the authoritative tenant
     * first ({@code AuthService.refresh} from the refresh-token payload,
     * {@code PasswordResetController.accept} from the consumed reset token). Without
     * that the two predicates contradict, the lookup returns null, and a single-use
     * credential that was already spent one statement earlier is lost.
     */
    @Select("""
            SELECT * FROM core_auth_user
             WHERE id = #{userId}
               AND tenant_id = #{tenantId}
               AND mark = 1
             LIMIT 1
            """)
    UserEntity findByIdAndTenant(@Param("userId") String userId,
                                 @Param("tenantId") String tenantId);

    /**
     * Looks up the business user for a given Keycloak UUID inside a tenant.
     * The (tenant_id, keycloak_id) pair has a partial unique index (see V21),
     * so this should return at most one row.
     *
     * <p>{@code tenant_id} is filtered explicitly; the interceptor appends its own
     * predicate on top (it rewrites hand-written SQL too — see
     * {@link #findByIdAndTenant}). The two agree because
     * {@code CoreRequestContextFilter} sets the context from the JWT {@code tid}
     * BEFORE invoking the OIDC resolver, which is exactly why that ordering is
     * load-bearing.
     */
    @Select("""
            SELECT * FROM core_auth_user
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND keycloak_id = #{keycloakId}
             LIMIT 1
            """)
    UserEntity findByKeycloakIdAndTenant(@Param("keycloakId") String keycloakId,
                                         @Param("tenantId") String tenantId);

    /**
     * Count SOFT-DELETED ({@code mark=0}) rows for a Keycloak id in a tenant.
     * Used by OIDC JIT to refuse re-provisioning a user that an admin deleted:
     * the deleted user's still-valid access token would otherwise resolve to no
     * {@code mark=1} row and silently create a brand-new roleless ghost account.
     * Hand-written {@code @Select} (explicit tenant_id) for the same interceptor
     * reason as the lookups above.
     */
    @Select("""
            SELECT COUNT(*) FROM core_auth_user
             WHERE mark = 0
               AND tenant_id = #{tenantId}
               AND keycloak_id = #{keycloakId}
            """)
    long countDeletedByKeycloakIdAndTenant(@Param("keycloakId") String keycloakId,
                                           @Param("tenantId") String tenantId);

    /**
     * Exact username lookup inside a tenant — deliberately NOT
     * {@link #findByIdentifier}.
     *
     * <p>{@code findByIdentifier} is the <em>login</em> matcher: it accepts
     * {@code username OR email OR user_no}, because a human typing into a login
     * box may use any of the three. That is wrong for machine-to-row binding.
     * The OIDC JIT resolver's legacy-bind branch feeds it the IdP's
     * {@code preferred_username} claim, so a Keycloak username that happens to
     * equal a DIFFERENT business user's {@code email} (entirely ordinary —
     * Keycloak's {@code registrationEmailAsUsername} makes email-shaped
     * usernames the norm) or {@code user_no} matched that other user's row.
     * The bind then wrote the SSO user's {@code keycloak_id} onto the victim's
     * row, so from the next request on the fast path resolved the SSO user to
     * the VICTIM's business user id — inheriting their roles, department and
     * data scope — and, unless the victim held SUPER_ADMIN, nulled their
     * {@code password_hash}, destroying their break-glass credential. With
     * {@code LIMIT 1} and no ORDER BY, which row won was unspecified.
     *
     * <p>{@code (tenant_id, username)} carries a partial unique index
     * ({@code uk_core_auth_user_tenant_username}, {@code WHERE mark = 1}), so
     * this lookup is exact — which is precisely what the bind branch's own
     * javadoc says it wants ("a row exists with the same (tenant_id, username)").
     *
     * <p>Hand-written {@code @Select} with an explicit {@code tenant_id} for the
     * same interceptor reason as the lookups above.
     */
    @Select("""
            SELECT * FROM core_auth_user
             WHERE mark = 1
               AND tenant_id = #{tenantId}
               AND username = #{username}
             LIMIT 1
            """)
    UserEntity findByUsernameAndTenant(@Param("tenantId") String tenantId,
                                       @Param("username") String username);

    /**
     * The row's {@code mark} for a given id, or null when no row exists at all —
     * an existence probe that can tell "soft-deleted" apart from "absent".
     *
     * <p>{@code selectById} cannot: {@code mark} is {@code @TableLogic}, so
     * MyBatis-Plus appends {@code AND mark = 1} and a soft-deleted row reads as
     * null. Callers that seed rows under a FIXED id ({@code DemoSeeder}) would
     * then re-insert an id the table still holds, and the primary key rejects it.
     * Hand-written SQL is not rewritten by the logic-delete handler.
     *
     * <p>No tenant predicate: the id is a globally unique primary key, and the
     * tenant interceptor still scopes the statement to the caller's tenant.
     */
    @Select("SELECT mark FROM core_auth_user WHERE id = #{id}")
    Integer findMarkById(@Param("id") String id);

    /**
     * Bring a soft-deleted row back. An {@code UpdateWrapper} cannot: it carries
     * the same {@code AND mark = 1} guard, so it never matches a {@code mark = 0}
     * row.
     *
     * @return rows updated — 1 when a soft-deleted row was revived, 0 otherwise
     */
    @Update("UPDATE core_auth_user SET mark = 1, update_time = #{now} WHERE id = #{id} AND mark = 0")
    int reviveById(@Param("id") String id, @Param("now") OffsetDateTime now);
}
