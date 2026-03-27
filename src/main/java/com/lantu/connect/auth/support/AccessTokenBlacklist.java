package com.lantu.connect.auth.support;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Access Token 登出黑名单（Redis），与 JWT 过滤器共用同一 key 规则。
 */
@Component
@RequiredArgsConstructor
public class AccessTokenBlacklist {

    public static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpirySeconds;

    public void add(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + sha256(rawToken), "1", Duration.ofSeconds(accessTokenExpirySeconds));
    }

    public boolean contains(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + sha256(rawToken)));
    }

    /**
     * 删除无 TTL 的黑名单键（异常写入时），正常登出键会随 access-token 过期自动删除。
     */
    public long removeOrphanBlacklistKeys() {
        try {
            Long removed = redisTemplate.execute((RedisCallback<Long>) connection -> {
                long n = 0;
                ScanOptions options = ScanOptions.scanOptions().match(BLACKLIST_PREFIX + "*").count(200).build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        byte[] key = cursor.next();
                        long ttl = connection.ttl(key);
                        if (ttl == -1) {
                            connection.del(key);
                            n++;
                        }
                    }
                }
                return n;
            });
            return removed != null ? removed : 0L;
        } catch (DataAccessException e) {
            return 0L;
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
