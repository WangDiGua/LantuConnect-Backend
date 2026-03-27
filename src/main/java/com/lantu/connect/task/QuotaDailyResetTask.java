package com.lantu.connect.task;

import com.lantu.connect.sysconfig.mapper.QuotaMapper;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务 QuotaDailyReset 定时任务
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuotaDailyResetTask {

    private static final String TASK_NAME = "QuotaDailyResetTask";

    private final QuotaMapper quotaMapper;
    private final TaskDistributedLock taskDistributedLock;

    @Scheduled(cron = "0 0 0 * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            int rows = quotaMapper.resetDailyUsed();
            log.info("{} reset daily_used, affected rows={}", TASK_NAME, rows);
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }
}
