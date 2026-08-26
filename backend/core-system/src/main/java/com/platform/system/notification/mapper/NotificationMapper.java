package com.platform.system.notification.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.notification.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Tenant interceptor: both statements below carry
 * {@code @InterceptorIgnore(tenantLine = "true")}, and it is REQUIRED, not
 * decorative. MyBatis-Plus rewrites ALL SQL — hand-written {@code @Select}
 * included — so without it each one additionally gets
 * {@code AND tenant_id = <RequestContext.tenantId()>} appended, which fights the
 * explicit predicate whenever the ambient context names a different tenant (or
 * none: a null context resolves to the {@code demo} fallback, so a thread with no
 * context reads the WRONG tenant rather than every tenant).
 *
 * <p>Both callers pass a tenant that does not come from the current request:
 * {@code NotificationEventListener} passes the event's, and
 * {@code NotificationController.stream} passes the one baked into the SSE ticket.
 * That stream endpoint is the sharp case — it is in
 * {@code SecurityConfig.PERMIT_PATHS} precisely because an {@code EventSource}
 * cannot send headers, so it carries no {@code Authorization} AND no
 * {@code X-Tenant-Id}, and {@code CoreRequestContextFilter} falls back to
 * {@code demo}. The connect frame therefore reported 0 unread for every tenant
 * except {@code demo}, resetting the badge on every connect and reconnect until
 * the 20s poll corrected it.
 *
 * <p>Safe because both statements are reads that already filter
 * {@code tenant_id} explicitly, and neither tenant value is caller-supplied (one
 * comes from the domain event, the other from a ticket minted server-side on an
 * authenticated request). Same precedent and reasoning as
 * {@code PasswordResetTokenMapper} / {@code UserInviteMapper}.
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

    /**
     * Distinct recipients of still-unread notifications for a business record —
     * used to know whom to push a fresh unread count after resolving. Explicit
     * tenant param: it comes from the domain event, not the request.
     */
    // See the class javadoc "Tenant interceptor" note — this annotation is REQUIRED.
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT recipient_user_id FROM core_notification
             WHERE mark = 1
               AND read_flag = 0
               AND tenant_id = #{tenantId}
               AND biz_type = #{bizType}
               AND biz_id = #{bizId}
            """)
    List<String> recipientsToResolve(@Param("tenantId") String tenantId,
                                     @Param("bizType") String bizType,
                                     @Param("bizId") String bizId);

    /**
     * Unread count with EXPLICIT tenant + recipient params, so it is correct on
     * any thread — the async push path and the ticket-authenticated SSE stream
     * handler included, neither of which has the caller's {@code RequestContext}.
     * Hand-written SQL alone does NOT achieve that; see the class javadoc.
     */
    // See the class javadoc "Tenant interceptor" note — this annotation is REQUIRED.
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(*) FROM core_notification
             WHERE mark = 1
               AND read_flag = 0
               AND tenant_id = #{tenantId}
               AND recipient_user_id = #{userId}
            """)
    long countUnread(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
