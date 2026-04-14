package com.lantu.connect.common.security;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * 认证类接口的 Redis 滑动窗口限流（按 IP / 用户名），补 Resilience4j 单机令牌桶无法区分来源的问题。
 */
@Component
@RequiredArgsConstructor
public class RedisAuthRateLimiter {

    private static final String LOGIN_IP = "lantu:rl:login:ip:";
    private static final String LOGIN_USER = "lantu:rl:login:user:";
    private static final String REGISTER_IP = "lantu:rl:reg:ip:";
    private static final String CAPTCHA_IP = "lantu:rl:captcha:ip:";
    private static final String API_KEY_REVOKE_USER = "lantu:rl:api-key-revoke:user:";

    private final StringRedisTemplate redisTemplate;

    @Value("${lantu.security.rate-limit.login-per-ip-per-minute:50}")
    private int loginPerIpPerMinute;

    @Value("${lantu.security.rate-limit.login-per-username-per-minute:25}")
    private int loginPerUsernamePerMinute;

    @Value("${lantu.security.rate-limit.register-per-ip-per-hour:40}")
    private int registerPerIpPerHour;

    @Value("${lantu.security.rate-limit.captcha-per-ip-per-minute:45}")
    private int captchaPerIpPerMinute;

    @Value("${lantu.security.rate-limit.api-key-revoke-per-user-per-hour:30}")
    private int apiKeyRevokePerUserPerHour;

    public void checkLogin(String clientIp, String username) {
        if (StringUtils.hasText(clientIp)) {
            enforceWindow(LOGIN_IP + normalize(clientIp), loginPerIpPerMinute, Duration.ofMinutes(1), "登录过于频繁，请稍后重试");
        }
        if (StringUtils.hasText(username)) {
            enforceWindow(LOGIN_USER + normalize(username), loginPerUsernamePerMinute, Duration.ofMinutes(1), "登录尝试过多，请稍后重试");
        }
    }

    public void checkRegister(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        enforceWindow(REGISTER_IP + normalize(clientIp), registerPerIpPerHour, Duration.ofHours(1), "注册过于频繁，请稍后再试");
    }

    public void checkCaptchaGenerate(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        enforceWindow(CAPTCHA_IP + normalize(clientIp), captchaPerIpPerMinute, Duration.ofMinutes(1), "验证码获取过于频繁");
    }

    /**
     * 按用户限流撤销 API Key 尝试次数，缓解密码暴力试探。
     */
    public void checkApiKeyRevokeByUser(Long userId) {
        if (userId == null) {
            return;
        }
        enforceWindow(API_KEY_REVOKE_USER + userId, apiKeyRevokePerUserPerHour, Duration.ofHours(1),
                "撤销尝试过于频繁，请稍后再试");
    }

    private void enforceWindow(String key, int max, Duration ttl, String message) {
        Long n = redisTemplate.opsForValue().increment(key);
        if (n == null) {
            return;
        }
        if (n == 1L) {
            redisTemplate.expire(key, Objects.requireNonNull(ttl));
        }
        if (n > max) {
            throw new BusinessException(ResultCode.RATE_LIMITED, message);
        }
    }

    private static String normalize(String s) {
        String t = s.trim().toLowerCase();
        if (t.length() > 200) {
            return t.substring(0, 200);
        }
        return t;
    }
}
