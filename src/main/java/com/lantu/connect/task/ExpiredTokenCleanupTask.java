package com.lantu.connect.task;

import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清理无 TTL 的登出黑名单键；正常登出键随 access token 过期由 Redis TTL 回收。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredTokenCleanupTask {

    private static final String TASK_NAME = "ExpiredTokenCleanupTask";

    private final TaskDistributedLock taskDistributedLock;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            long removed = accessTokenBlacklist.removeOrphanBlacklistKeys();
            log.info("{} removed {} orphan blacklist keys", TASK_NAME, removed);
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }
}
