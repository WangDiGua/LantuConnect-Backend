package com.lantu.connect.notification.service.impl;

import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.notification.service.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final UserMapper userMapper;

    @Value("${spring.mail.username:noreply@lantuconnect.com}")
    private String fromAddress;

    @Override
    public String channelName() {
        return "email";
    }

    @Override
    public boolean supports(String channelType) {
        return "email".equalsIgnoreCase(channelType);
    }

    @Override
    public void deliver(Long userId, String title, String body) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getMail() == null || user.getMail().isBlank()) {
            log.debug("User {} has no email address, skipping email notification", userId);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getMail());
            message.setSubject("[LantuConnect] " + title);
            message.setText(body);
            mailSender.send(message);
            log.info("Email notification sent to user {} ({})", userId, user.getMail());
        } catch (Exception e) {
            log.warn("Failed to send email notification to user {}: {}", userId, e.getMessage());
        }
    }
}
