package com.lantu.connect.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.web.ServletContextPathUtil;
import com.lantu.connect.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 应用启动短时令牌：后端签发，单次消费后跳转到真实 app_url。
 */
@Service
@RequiredArgsConstructor
public class AppLaunchTokenService {

    private static final String KEY_PREFIX = "lantu:app:launch:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lantu.integration.app-launch-token-ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${server.servlet.context-path:/regis}")
    private String servletContextPath;

    public LaunchTicket issue(Long resourceId, String appUrl, String apiKeyId, Long userId) {
        if (resourceId == null || !StringUtils.hasText(appUrl) || !StringUtils.hasText(apiKeyId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "应用启动参数不完整");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        AppLaunchClaims claims = new AppLaunchClaims(resourceId, appUrl.trim(), apiKeyId.trim(), userId, LocalDateTime.now());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(claims);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "应用启动令牌生成失败");
        }
        long ttl = Math.max(30L, Math.min(1800L, ttlSeconds));
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + token, payload, Duration.ofSeconds(ttl));
        return new LaunchTicket(token, ServletContextPathUtil.join(servletContextPath, "/catalog/apps/launch") + "?token=" + token);
    }

    public AppLaunchClaims consume(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少 launch token");
        }
        String key = KEY_PREFIX + token.trim();
        String payload = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(payload)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "launch token 无效或已过期");
        }
        stringRedisTemplate.delete(key);
        try {
            return objectMapper.readValue(payload, AppLaunchClaims.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "launch token 解析失败");
        }
    }

    public record LaunchTicket(String token, String launchUrl) {
    }

    public record AppLaunchClaims(Long resourceId, String appUrl, String apiKeyId, Long userId, LocalDateTime issuedAt) {
    }
}
