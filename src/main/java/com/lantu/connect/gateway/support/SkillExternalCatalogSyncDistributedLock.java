package com.lantu.connect.gateway.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 技能市场全量同步跨实例锁：SET key NX PX + Lua 校验 token 后删除，避免误删其他实例的锁。
 */
@Component
@Slf4j
public class SkillExternalCatalogSyncDistributedLock {

    public static final String LOCK_KEY = "lantu:lock:skill-external-catalog-sync";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();
    static {
        RELEASE_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final String lockOwnerPrefix;

    public SkillExternalCatalogSyncDistributedLock(
            StringRedisTemplate stringRedisTemplate,
            @Value("${spring.application.name:lantu-connect}") String applicationName) {
        this.stringRedisTemplate = stringRedisTemplate;
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "unknown-host";
            }
        }
        this.lockOwnerPrefix = applicationName + "@" + host + ":";
    }

    /**
     * @return 持锁 token（释放时原样传入），未获得锁时 empty
     */
    public Optional<String> tryAcquire(long ttlMs) {
        String token = lockOwnerPrefix + UUID.randomUUID();
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, Duration.ofMillis(ttlMs));
        if (Boolean.TRUE.equals(ok)) {
            return Optional.of(token);
        }
        return Optional.empty();
    }

    public void releaseQuietly(String token) {
        if (token == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(LOCK_KEY), token);
        } catch (RuntimeException e) {
            log.warn("释放技能市场同步 Redis 锁失败（可忽略若已过期）: {}", e.getMessage());
        }
    }

    public boolean exists() {
        try {
            Boolean has = stringRedisTemplate.hasKey(LOCK_KEY);
            return Boolean.TRUE.equals(has);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 判断是否宜按「Redis 不可用」降级为 JVM 单飞（避免仅识别 {@link RedisConnectionFailureException} 而漏掉 Lettuce 封装/超时等）。
     */
    public static boolean isRedisLockLikelyFailure(Throwable e) {
        if (e == null) {
            return false;
        }
        Set<Throwable> seen = new HashSet<>();
        for (Throwable c = e; c != null && seen.add(c); c = c.getCause()) {
            if (c instanceof RedisConnectionFailureException) {
                return true;
            }
            if (c instanceof ConnectException || c instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
