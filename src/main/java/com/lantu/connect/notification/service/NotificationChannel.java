package com.lantu.connect.notification.service;

/**
 * Abstraction for notification delivery channels.
 */
public interface NotificationChannel {

    String channelName();

    boolean supports(String channelType);

    void deliver(Long userId, String title, String body);
}
