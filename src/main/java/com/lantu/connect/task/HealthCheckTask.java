package com.lantu.connect.task;

import com.lantu.connect.monitoring.ResourceCircuitHealthBridge;
import com.lantu.connect.realtime.RealtimePushService;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class HealthCheckTask {

    private static final String TASK_NAME = "HealthCheck";
    private static final Map<Long, Integer> CONSECUTIVE_FAILURES = new ConcurrentHashMap<>();

    private final TaskDistributedLock taskDistributedLock;
    private final JdbcTemplate jdbcTemplate;
    private final RealtimePushService realtimePushService;
    private final ResourceCircuitHealthBridge resourceCircuitHealthBridge;
    private final ResourceHealthService resourceHealthService;

    @Scheduled(cron = "0 */1 * * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, resource_id, resource_type, resource_code, display_name, check_type, check_url, timeout_sec, "
                            + "healthy_threshold, interval_sec, health_status FROM t_resource_runtime_policy "
                            + "WHERE check_url IS NOT NULL AND TRIM(check_url) <> '' "
                            + "AND (resource_type IN ('agent', 'skill') OR check_type IS NULL OR LOWER(TRIM(check_type)) IN ('', 'http'))");
            int healthy = 0, degraded = 0, down = 0;
            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                Long resourceId = row.get("resource_id") instanceof Number n ? n.longValue() : null;
                String resourceType = row.get("resource_type") == null ? null : String.valueOf(row.get("resource_type"));
                String resourceCode = row.get("resource_code") == null ? null : String.valueOf(row.get("resource_code"));
                String displayName = row.get("display_name") == null ? null : String.valueOf(row.get("display_name"));
                String checkTypeVal = row.get("check_type") == null ? "http" : String.valueOf(row.get("check_type"));
                String prevHealth = row.get("health_status") == null ? null : String.valueOf(row.get("health_status")).trim();
                String url = String.valueOf(row.get("check_url"));
                int timeoutSec = 10;
                int failThreshold = 3;
                Object to = row.get("timeout_sec");
                if (to instanceof Number n) {
                    timeoutSec = Math.max(1, Math.min(120, n.intValue()));
                }
                Object ht = row.get("healthy_threshold");
                if (ht instanceof Number n) {
                    failThreshold = Math.max(1, Math.min(20, n.intValue()));
                }
                if ("agent".equalsIgnoreCase(resourceType) || "skill".equalsIgnoreCase(resourceType)) {
                    var snapshot = resourceHealthService.probeAndPersist(resourceId);
                    String status = snapshot == null ? "down" : String.valueOf(snapshot.getHealthStatus());
                    if ("healthy".equalsIgnoreCase(status)) {
                        healthy++;
                    } else if ("degraded".equalsIgnoreCase(status)) {
                        degraded++;
                    } else {
                        down++;
                    }
                    continue;
                }
                boolean ok = probe(url, timeoutSec);
                String status;
                if (ok) {
                    CONSECUTIVE_FAILURES.remove(id);
                    status = "healthy";
                    healthy++;
                } else {
                    int failures = CONSECUTIVE_FAILURES.getOrDefault(id, 0) + 1;
                    CONSECUTIVE_FAILURES.put(id, failures);
                    if (failures >= failThreshold) {
                        status = "down";
                        down++;
                    } else {
                        status = "degraded";
                        degraded++;
                    }
                }
                LocalDateTime checkedAt = LocalDateTime.now();
                jdbcTemplate.update(
                        "UPDATE t_resource_runtime_policy SET health_status = ?, last_check_time = ? WHERE id = ?",
                        status, checkedAt, id);
                if ("healthy".equalsIgnoreCase(status)
                        && resourceId != null
                        && StringUtils.hasText(resourceType)) {
                    resourceCircuitHealthBridge.resetOpenOrHalfOpenAfterHealthyProbe(resourceType, resourceId);
                }
                if (resourceId != null
                        && !normHealthStatus(prevHealth).equals(normHealthStatus(status))) {
                    realtimePushService.pushHealthProbeStatusChanged(
                            resourceId,
                            resourceType,
                            resourceCode,
                            displayName != null ? displayName : resourceCode,
                            checkTypeVal,
                            status,
                            prevHealth,
                            checkedAt);
                }
            }
            if (rows.size() > 0) {
                log.info("[定时任务] {} 完成: {} 个目标 (健康: {}, 降级: {}, 下线: {})",
                        TASK_NAME, rows.size(), healthy, degraded, down);
            }
        } catch (DataAccessException e) {
            log.warn("[定时任务] {} 失败: {}", TASK_NAME, e.getMessage());
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }

    private static String normHealthStatus(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static boolean probe(String url, int timeoutSec) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSec))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .GET()
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            int code = resp.statusCode();
            return code >= 200 && code < 400;
        } catch (java.io.IOException | InterruptedException e) {
            return false;
        }
    }
}
