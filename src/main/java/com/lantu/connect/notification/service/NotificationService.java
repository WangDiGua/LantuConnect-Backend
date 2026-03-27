package com.lantu.connect.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.notification.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知Notification服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface NotificationService {

    void send(Notification notification);

    void broadcast(List<Long> userIds, String type, String title, String body, String sourceType, Long sourceId);

    Page<Notification> listByUser(Long userId, int page, int pageSize);

    Page<Notification> listByUser(Long userId, int page, int pageSize, String type, Boolean isRead,
                                  LocalDateTime startTime, LocalDateTime endTime);

    Notification getById(Long userId, Long notificationId);

    long unreadCount(Long userId);

    void markAsRead(Long userId, Long notificationId);

    void markAllAsRead(Long userId);
}
