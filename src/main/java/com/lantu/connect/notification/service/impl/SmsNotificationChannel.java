package com.lantu.connect.notification.service.impl;

import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.common.service.SmsService;
import com.lantu.connect.notification.service.NotificationChannel;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationChannel implements NotificationChannel {

    private final SmsService smsService;
    private final UserMapper userMapper;
    private final RuntimeAppConfigService runtimeAppConfigService;

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
        if (!runtimeAppConfigService.notification().isSmsEnabled()) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getMobile() == null || user.getMobile().isBlank()) {
            log.debug("User {} has no phone number, skipping SMS notification", userId);
            return;
        }
        try {
            smsService.send(user.getMobile(), "[NexusAI] " + title + ": " + body);
            log.info("SMS notification sent to user {} ({})", userId, user.getMobile());
        } catch (Exception e) {
            log.warn("Failed to send SMS notification to user {}: {}", userId, e.getMessage());
        }
    }
}
