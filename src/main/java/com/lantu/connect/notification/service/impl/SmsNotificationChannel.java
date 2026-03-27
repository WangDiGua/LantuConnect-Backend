package com.lantu.connect.notification.service.impl;

import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.common.service.SmsService;
import com.lantu.connect.notification.service.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lantu.notification.sms-enabled", havingValue = "true")
public class SmsNotificationChannel implements NotificationChannel {

    private final SmsService smsService;
    private final UserMapper userMapper;

    @Override
    public String channelName() {
        return "sms";
    }

    @Override
    public boolean supports(String channelType) {
        return "sms".equalsIgnoreCase(channelType);
    }

    @Override
    public void deliver(Long userId, String title, String body) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getMobile() == null || user.getMobile().isBlank()) {
            log.debug("User {} has no phone number, skipping SMS notification", userId);
            return;
        }
        try {
            smsService.send(user.getMobile(), "[LantuConnect] " + title + ": " + body);
            log.info("SMS notification sent to user {} ({})", userId, user.getMobile());
        } catch (Exception e) {
            log.warn("Failed to send SMS notification to user {}: {}", userId, e.getMessage());
        }
    }
}
