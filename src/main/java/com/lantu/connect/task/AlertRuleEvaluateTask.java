package com.lantu.connect.task;

import com.lantu.connect.monitoring.service.AlertCenterService;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertRuleEvaluateTask {

    private static final String TASK_NAME = "AlertRuleEvaluateTask";

    private final TaskDistributedLock taskDistributedLock;
    private final AlertCenterService alertCenterService;

    @Scheduled(cron = "0 */1 * * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            alertCenterService.evaluateEnabledRules();
        } catch (RuntimeException ex) {
            log.warn("{} failed: {}", TASK_NAME, ex.getMessage());
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }
}
