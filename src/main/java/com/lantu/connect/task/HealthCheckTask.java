package com.lantu.connect.task;

import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.realtime.RealtimePushService;
import com.lantu.connect.monitoring.ResourceCircuitHealthBridge;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HealthCheckTask {

    private static final String TASK_NAME = "HealthCheck";

    private final TaskDistributedLock taskDistributedLock;
    private final JdbcTemplate jdbcTemplate;
    @SuppressWarnings("unused")
    private final RealtimePushService realtimePushService;
    @SuppressWarnings("unused")
    private final ResourceCircuitHealthBridge resourceCircuitHealthBridge;
    private final ResourceHealthService resourceHealthService;

    @Scheduled(cron = "0 */1 * * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            List<Long> targets = jdbcTemplate.query("""
                            SELECT r.id
                            FROM t_resource r
                            LEFT JOIN t_resource_runtime_policy p ON p.resource_id = r.id
                            WHERE r.deleted = 0
                              AND r.resource_type IN ('agent', 'skill')
                              AND LOWER(r.status) = 'published'
                              AND LOWER(COALESCE(p.health_status, '')) <> 'disabled'
                              AND (p.last_probe_at IS NULL OR p.interval_sec IS NULL
                                   OR TIMESTAMPDIFF(SECOND, p.last_probe_at, NOW()) >= p.interval_sec)
                            ORDER BY p.resource_id ASC
                            """,
                    (rs, i) -> rs.getLong(1));
            int healthy = 0;
            int degraded = 0;
            int down = 0;
            for (Long resourceId : targets) {
                ResourceHealthSnapshotVO snapshot = resourceHealthService.probeAndPersist(resourceId);
                String status = snapshot == null ? "down" : String.valueOf(snapshot.getHealthStatus());
                if ("healthy".equalsIgnoreCase(status)) {
                    healthy++;
                } else if ("degraded".equalsIgnoreCase(status)) {
                    degraded++;
                } else {
                    down++;
                }
            }
            if (!targets.isEmpty()) {
                log.info("[定时任务] {} 完成: {} 个目标 (健康: {}, 降级: {}, 下线: {})",
                        TASK_NAME, targets.size(), healthy, degraded, down);
            }
        } catch (DataAccessException ex) {
            log.warn("[定时任务] {} 失败: {}", TASK_NAME, ex.getMessage());
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }
}
