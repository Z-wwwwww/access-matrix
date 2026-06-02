package com.platform.system.notification.dto;

import java.time.LocalDateTime;

public final class NotificationDto {

    private NotificationDto() {}

    /** Row returned by {@code /notification/list}. */
    public record View(
            String id,
            String type,
            String title,
            String content,
            String link,
            String bizType,
            String bizId,
            Integer kind,
            Integer level,
            Integer readFlag,
            LocalDateTime readTime,
            LocalDateTime createTime) {}
}
