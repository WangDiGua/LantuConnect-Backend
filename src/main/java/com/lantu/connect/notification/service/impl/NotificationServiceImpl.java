package com.lantu.connect.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.notification.entity.Notification;
import com.lantu.connect.notification.mapper.NotificationMapper;
import com.lantu.connect.notification.service.NotificationService;
import com.lantu.connect.realtime.RealtimePushService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知Notification服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final RealtimePushService realtimePushService;

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void send(Notification notification) {
        if (notification.getIsRead() == null) {
            notification.setIsRead(false);
        }
        notificationMapper.insert(notification);
        publishCreated(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void broadcast(List<Long> userIds, String type, String title, String body, String sourceType, Long sourceId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long uid : userIds) {
            Notification n = new Notification();
            n.setUserId(uid);
            n.setType(type);
            n.setTitle(title);
            n.setBody(body);
            n.setSourceType(sourceType);
            n.setSourceId(sourceId != null ? String.valueOf(sourceId) : null);
            n.setIsRead(false);
            notificationMapper.insert(n);
            publishCreated(n);
        }
    }

    @Override
    public Page<Notification> listByUser(Long userId, int page, int pageSize) {
        return listByUser(userId, page, pageSize, null, null, null, null);
    }

    @Override
    public Page<Notification> listByUser(Long userId, int page, int pageSize, String type, Boolean isRead,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        Page<Notification> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notification> q = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(isRead != null, Notification::getIsRead, isRead)
                .ge(startTime != null, Notification::getCreateTime, startTime)
                .le(endTime != null, Notification::getCreateTime, endTime)
                .orderByDesc(Notification::getCreateTime);
        if (StringUtils.hasText(type)) {
            q.eq(Notification::getType, type.trim());
        }
        return notificationMapper.selectPage(p, q);
    }

    @Override
    public Notification getById(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !userId.equals(n.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        return n;
    }

    @Override
    public long unreadCount(Long userId) {
        Long c = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false));
        return c == null ? 0 : c;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !userId.equals(n.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        n.setIsRead(true);
        notificationMapper.updateById(n);
        realtimePushService.pushUnreadSync(userId, unreadCount(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true));
        realtimePushService.pushUnreadSync(userId, 0);
    }

    private void publishCreated(Notification n) {
        if (n == null || n.getUserId() == null) {
            return;
        }
        realtimePushService.pushNotificationCreated(n.getUserId(), n, unreadCount(n.getUserId()));
    }
}
