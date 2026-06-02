package com.platform.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.notification.NotificationEvent;
import com.platform.core.common.result.PageResult;
import com.platform.system.notification.dto.NotificationDto;
import com.platform.system.notification.entity.NotificationEntity;
import com.platform.system.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationMapper mapper;

    public NotificationService(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Persist a notification row. Called from the async listener thread, so
     * {@code tenant_id} is set explicitly from the event (the audit filler's
     * {@code RequestContext} fallback would otherwise write "default").
     */
    @Transactional
    public String create(NotificationEvent e) {
        NotificationEntity n = new NotificationEntity();
        n.setTenantId(e.tenantId());          // explicit — async thread has no RequestContext
        n.setRecipientUserId(e.recipientUserId());
        n.setType(e.type());
        n.setTitle(e.title());
        n.setContent(e.content());
        n.setLink(e.link());
        n.setBizType(e.bizType());
        n.setBizId(e.bizId());
        n.setKind(e.kind());
        n.setLevel(1);
        n.setReadFlag(0);
        mapper.insert(n);                      // id / mark / audit cols auto-filled
        return n.getId();
    }

    /** Unread count for a user — explicit tenant, safe on any thread. */
    public long unreadCount(String tenantId, String userId) {
        return mapper.countUnread(tenantId, userId);
    }

    /** Paged inbox for the given user, newest first. */
    public PageResult<NotificationDto.View> list(String userId, long page, long size, Integer readFlag) {
        Page<NotificationEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<NotificationEntity> w = new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getMark, 1)
                .eq(NotificationEntity::getRecipientUserId, userId)
                .orderByDesc(NotificationEntity::getCreateTime);
        if (readFlag != null) {
            w.eq(NotificationEntity::getReadFlag, readFlag);
        }
        // The MyBatis-Plus tenant interceptor adds WHERE tenant_id=? from RequestContext.
        Page<NotificationEntity> res = mapper.selectPage(p, w);
        List<NotificationDto.View> records = res.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, res.getTotal(), page, size);
    }

    /** Mark one notification read — only if it belongs to the caller. */
    @Transactional
    public void markRead(String id, String userId) {
        NotificationEntity n = mapper.selectById(id);   // tenant-scoped by interceptor
        if (n == null || n.getMark() == null || n.getMark() != 1
                || !userId.equals(n.getRecipientUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found: " + id);
        }
        if (n.getReadFlag() != null && n.getReadFlag() == 1) return;   // idempotent
        n.setReadFlag(1);
        n.setReadTime(LocalDateTime.now());
        mapper.updateById(n);                            // @Version on update_time guards races
    }

    /**
     * Mark every still-unread notification pointing at {@code bizType+bizId} as
     * read (called when the business decision behind an action notification is
     * done). Returns the distinct recipients whose rows changed, so the caller
     * can push them a fresh unread count. Runs on an async listener thread, so
     * tenant is passed explicitly for the recipient lookup.
     */
    @Transactional
    public List<String> markReadByBiz(String tenantId, String bizType, String bizId) {
        List<String> recipients = mapper.recipientsToResolve(tenantId, bizType, bizId);
        if (recipients.isEmpty()) return recipients;
        LambdaUpdateWrapper<NotificationEntity> u = new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getBizType, bizType)
                .eq(NotificationEntity::getBizId, bizId)
                .eq(NotificationEntity::getReadFlag, 0)
                .set(NotificationEntity::getReadFlag, 1)
                .set(NotificationEntity::getReadTime, LocalDateTime.now());
        mapper.update(null, u);   // tenant interceptor scopes (RequestContext set by listener)
        return recipients;
    }

    /** Mark every unread notification of the caller as read. */
    @Transactional
    public void markAllRead(String userId) {
        LambdaUpdateWrapper<NotificationEntity> u = new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientUserId, userId)
                .eq(NotificationEntity::getReadFlag, 0)
                .set(NotificationEntity::getReadFlag, 1)
                .set(NotificationEntity::getReadTime, LocalDateTime.now());
        mapper.update(null, u);   // tenant interceptor scopes the UPDATE
    }

    private NotificationDto.View toView(NotificationEntity n) {
        return new NotificationDto.View(
                n.getId(), n.getType(), n.getTitle(), n.getContent(), n.getLink(),
                n.getBizType(), n.getBizId(), n.getKind(),
                n.getLevel(), n.getReadFlag(), n.getReadTime(), n.getCreateTime());
    }
}
