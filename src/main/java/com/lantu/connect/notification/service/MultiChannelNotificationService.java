package com.lantu.connect.notification.service;

import com.lantu.connect.common.tx.TransactionCommitExecutor;
import com.lantu.connect.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Dispatches notifications to all available channels (in-app + email + SMS).
 * Falls back gracefully when optional channels are not configured.
 */
@Slf4j
@Service
public class MultiChannelNotificationService {

    private final NotificationService notificationService;
    private final List<NotificationChannel> channels;

    public MultiChannelNotificationService(NotificationService notificationService,
                                           List<NotificationChannel> channels) {
        this.notificationService = notificationService;
        this.channels = channels != null ? channels : List.of();
        log.info("MultiChannelNotificationService initialized with {} extra channels: {}",
                this.channels.size(),
                this.channels.stream().map(NotificationChannel::channelName).toList());
    }

    public void sendAll(Long userId, String type, String title, String body, String sourceType, String sourceId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setSourceType(sourceType);
        n.setSourceId(sourceId);
        sendAll(n);
    }

    public void sendAll(Notification n) {
        if (n == null) {
            return;
        }
        TransactionCommitExecutor.runAfterCommitOrNow(() -> dispatch(n));
    }

    private void dispatch(Notification notification) {
        notificationService.send(notification);
        deliverAdditionalChannels(notification);
    }

    @Async
    void deliverAdditionalChannels(Notification notification) {
        for (NotificationChannel channel : channels) {
            try {
                channel.deliver(notification.getUserId(), notification.getTitle(), notification.getBody());
            } catch (RuntimeException e) {
                log.warn("Channel {} failed for user {}: {}", channel.channelName(), notification.getUserId(), e.getMessage());
            }
        }
    }
}
