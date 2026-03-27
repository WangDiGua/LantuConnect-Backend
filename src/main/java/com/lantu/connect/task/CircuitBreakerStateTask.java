package com.lantu.connect.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.monitoring.entity.CircuitBreaker;
import com.lantu.connect.monitoring.mapper.CircuitBreakerMapper;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务 CircuitBreakerState 定时任务
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerStateTask {

    private static final String TASK_NAME = "CircuitBreakerStateTask";

    private final CircuitBreakerMapper circuitBreakerMapper;
    private final TaskDistributedLock taskDistributedLock;

    @Scheduled(cron = "0 */1 * * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            List<CircuitBreaker> openList = circuitBreakerMapper.selectList(
                    new LambdaQueryWrapper<CircuitBreaker>().eq(CircuitBreaker::getCurrentState, CircuitBreaker.STATE_OPEN));
            LocalDateTime now = LocalDateTime.now();
            for (CircuitBreaker cb : openList) {
                if (cb.getLastOpenedAt() == null || cb.getOpenDurationSec() == null) {
                    continue;
                }
                LocalDateTime until = cb.getLastOpenedAt().plusSeconds(cb.getOpenDurationSec());
                if (!now.isBefore(until)) {
                    cb.setCurrentState(CircuitBreaker.STATE_HALF_OPEN);
                    cb.setSuccessCount(0L);
                    cb.setFailureCount(0L);
                    cb.setUpdateTime(now);
                    circuitBreakerMapper.updateById(cb);
                    log.info("{} transitioned {} to HALF_OPEN", TASK_NAME, cb.getAgentName());
                }
            }
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }
}
