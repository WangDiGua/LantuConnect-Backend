package com.lantu.connect.task.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ThreadLocal<Map<String, String>> lockOwners = ThreadLocal.withInitial(HashMap::new);

    public boolean tryLock(String taskName) {
        String owner = UUID.randomUUID().toString();
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(PREFIX + taskName, owner, Duration.ofMinutes(10));
        if (Boolean.TRUE.equals(ok)) {
            lockOwners.get().put(taskName, owner);
            return true;
        }
        return false;
    }

    public void unlock(String taskName) {
        Map<String, String> owners = lockOwners.get();
        String owner = owners.remove(taskName);
        if (owners.isEmpty()) {
            lockOwners.remove();
        }
        if (owner == null) {
            return;
        }
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(PREFIX + taskName), owner);
    }
}
