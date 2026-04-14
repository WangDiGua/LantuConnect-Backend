package com.lantu.connect.auth.support;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * 按会话 ID 失效仍有效的 Access JWT（与 killSession / 服务端踢线配合，TTL 与 access token 一致）。
 */
@Component
@RequiredArgsConstructor
public class SessionRevocationRegistry {

    static final String REVOKED_PREFIX = "lantu:session:revoked:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpirySeconds;

    public void revoke(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        redisTemplate.opsForValue().set(
                REVOKED_PREFIX + sessionId, "1", Objects.requireNonNull(Duration.ofSeconds(accessTokenExpirySeconds)));
    }

    public boolean isRevoked(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_PREFIX + sessionId));
    }
}
