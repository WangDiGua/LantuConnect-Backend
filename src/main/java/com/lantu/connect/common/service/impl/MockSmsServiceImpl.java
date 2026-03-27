package com.lantu.connect.common.service.impl;

import com.lantu.connect.common.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(name = "lantu.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsServiceImpl implements SmsService {

    @Override
    public void send(String phone, String content) {
        log.info("[MockSMS] 发送短信到 {}: {}", phone, content);
    }

    @Override
    public void sendVerifyCode(String phone, String code) {
        log.info("[MockSMS] 发送验证码到 {}: {}", phone, code);
    }
}
