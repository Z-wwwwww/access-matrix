package com.platform.system.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.system.notification.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

    /**
     * Distinct recipients of still-unread notifications for a business record —
     * used to know whom to push a fresh unread count after resolving. Explicit
     * tenant param (runs on a background thread without RequestContext).
     */
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
     * Unread count with EXPLICIT tenant + recipient params. Hand-written so it
     * works on background threads (the SSE stream handler and the push path)
     * where {@code RequestContext} — and therefore the MyBatis-Plus tenant
     * interceptor's tenant — is not populated. Same bypass rationale as
     * {@code PasswordResetTokenMapper.findActiveByTokenHash}.
     */
    @Select("""
            SELECT COUNT(*) FROM core_notification
             WHERE mark = 1
               AND read_flag = 0
               AND tenant_id = #{tenantId}
               AND recipient_user_id = #{userId}
            """)
    long countUnread(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
