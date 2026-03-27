package com.lantu.connect.task;

import com.lantu.connect.sysconfig.mapper.QuotaMapper;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务 QuotaMonthlyReset 定时任务
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuotaMonthlyResetTask {

    private static final String TASK_NAME = "QuotaMonthlyResetTask";

    private final QuotaMapper quotaMapper;
    private final TaskDistributedLock taskDistributedLock;

    @Scheduled(cron = "0 0 0 1 * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            int rows = quotaMapper.resetMonthlyUsed();
            log.info("{} reset monthly_used, affected rows={}", TASK_NAME, rows);
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }
}
