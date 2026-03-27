package com.lantu.connect.task.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时任务分布式锁工具
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Component
@RequiredArgsConstructor
public class TaskDistributedLock {

    private static final String PREFIX = "lantu:lock:";

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryLock(String taskName) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(PREFIX + taskName, "1", Duration.ofMinutes(10));
        return Boolean.TRUE.equals(ok);
    }

    public void unlock(String taskName) {
        stringRedisTemplate.delete(PREFIX + taskName);
    }
}
