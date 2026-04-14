package com.lantu.connect.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.McpConnectivityProbeRequest;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已发布 MCP 资源的周期性 JSON-RPC 探活：写入 {@code t_resource_runtime_policy}（{@code check_type=mcp_jsonrpc}），
 * 与仅做 HTTP GET 的 {@link HealthCheckTask} 分离，避免对 MCP 端点误发 GET。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpAutoHealthProbeTask {

    private static final String TASK_NAME = "McpAutoHealthProbe";
    private static final String CHECK_TYPE_MCP = "mcp_jsonrpc";
    private static final String TYPE_MCP = "mcp";
    private static final String STATUS_PUBLISHED = "published";
    /** 与 {@link McpConnectivityProbeService} 默认探测超时一致 */
    private static final int DEFAULT_PROBE_TIMEOUT_SEC = 20;

    private static final Map<Long, Integer> CONSECUTIVE_FAILURES = new ConcurrentHashMap<>();

    private final TaskDistributedLock taskDistributedLock;
    private final JdbcTemplate jdbcTemplate;
    private final McpConnectivityProbeService mcpConnectivityProbeService;
    private final ObjectMapper objectMapper;
    private final RealtimePushService realtimePushService;
    private final ResourceCircuitHealthBridge resourceCircuitHealthBridge;
    private final ResourceHealthService resourceHealthService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                    "SELECT r.id AS resource_id, r.resource_type, r.resource_code, r.display_name, "
                            + "m.endpoint, m.protocol, m.auth_type, m.auth_config "
                            + "FROM t_resource r INNER JOIN t_resource_mcp_ext m ON m.resource_id = r.id "
                            + "WHERE r.deleted = 0 AND LOWER(r.status) = ? AND r.resource_type = ?",
                    STATUS_PUBLISHED, TYPE_MCP);
            int ok = 0, degraded = 0, down = 0, skipped = 0;
            for (Map<String, Object> row : targets) {
                Long resourceId = ((Number) row.get("resource_id")).longValue();
                String endpoint = trimToNull(row.get("endpoint"));
                if (!StringUtils.hasText(endpoint)) {
                    skipped++;
                    continue;
                }
                String protoCol = trimToNull(row.get("protocol"));
                Map<String, Object> health = jdbcTemplate.queryForList(
                        "SELECT id, check_type, health_status, current_state, consecutive_success, consecutive_failure, "
                                + "last_success_at, last_failure_at, last_failure_reason, healthy_threshold, timeout_sec "
                                + "FROM t_resource_runtime_policy WHERE resource_id = ? LIMIT 1",
                        resourceId)
                        .stream().findFirst().orElse(null);
                if (health != null) {
                    String hs = trimToNull(health.get("health_status"));
                    if (hs != null && "disabled".equalsIgnoreCase(hs)) {
                        skipped++;
                        continue;
                    }
                    String ct = trimToNull(health.get("check_type"));
                    if (ct != null && !CHECK_TYPE_MCP.equalsIgnoreCase(ct)) {
                        skipped++;
                        continue;
                    }
                }
                if (protoCol != null && "stdio".equalsIgnoreCase(protoCol)) {
                    var snapshot = resourceHealthService.probeAndPersist(resourceId);
                    String status = snapshot == null ? "down" : String.valueOf(snapshot.getHealthStatus());
                    if ("healthy".equalsIgnoreCase(status)) {
                        ok++;
                    } else if ("degraded".equalsIgnoreCase(status)) {
                        degraded++;
                    } else {
                        down++;
                    }
                    continue;
                }
                int failThreshold = 3;
                if (health != null) {
                    Object ht = health.get("healthy_threshold");
                    if (ht instanceof Number n) {
                        failThreshold = Math.max(1, Math.min(20, n.intValue()));
                    }
                }
                Map<String, Object> authConfig = parseJsonMap(row.get("auth_config"));
                McpConnectivityProbeRequest probeReq = new McpConnectivityProbeRequest();
                probeReq.setEndpoint(endpoint.trim());
                String authType = trimToNull(row.get("auth_type"));
                if (authType != null) {
                    probeReq.setAuthType(authType);
                }
                if (!authConfig.isEmpty()) {
                    probeReq.setAuthConfig(new LinkedHashMap<>(authConfig));
                }
                String transport = resolveTransport(authConfig, protoCol, endpoint);
                if (transport != null) {
                    probeReq.setTransport(transport);
                }
                McpConnectivityProbeResult probeRes = mcpConnectivityProbeService.probe(probeReq);
                boolean probeOk = probeRes.isOk();

                String status;
                if (probeOk) {
                    CONSECUTIVE_FAILURES.remove(resourceId);
                    status = "healthy";
                    ok++;
                } else {
                    int failures = CONSECUTIVE_FAILURES.getOrDefault(resourceId, 0) + 1;
                    CONSECUTIVE_FAILURES.put(resourceId, failures);
                    if (failures >= failThreshold) {
                        status = "down";
                        down++;
                    } else {
                        status = "degraded";
                        degraded++;
                    }
                }
                LocalDateTime now = LocalDateTime.now();
                String resourceCode = valueOf(row.get("resource_code"));
                String displayName = valueOf(row.get("display_name"));
                String prevProbeStatus = health == null ? null : trimToNull(health.get("health_status"));
                long prevSuccess = health == null ? 0L : longValue(health.get("consecutive_success"));
                long prevFailure = health == null ? 0L : longValue(health.get("consecutive_failure"));
                LocalDateTime prevLastSuccessAt = health == null ? null : toDateTime(health.get("last_success_at"));
                LocalDateTime prevLastFailureAt = health == null ? null : toDateTime(health.get("last_failure_at"));
                if (health == null) {
                    jdbcTemplate.update(
                            "INSERT INTO t_resource_runtime_policy (resource_id, resource_type, resource_code, display_name, "
                                    + "check_type, check_url, interval_sec, healthy_threshold, timeout_sec, health_status, current_state, "
                                    + "last_check_time, last_probe_at, last_success_at, last_failure_at, last_failure_reason, "
                                    + "consecutive_success, consecutive_failure, probe_latency_ms, probe_payload_summary) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, 300, ?, ?, ?, 'CLOSED', ?, ?, ?, ?, ?, 0, 0, ?, ?)",
                            resourceId,
                            TYPE_MCP,
                            resourceCode,
                            StringUtils.hasText(displayName) ? displayName : resourceCode,
                            CHECK_TYPE_MCP,
                            endpoint.trim(),
                            failThreshold,
                            DEFAULT_PROBE_TIMEOUT_SEC,
                            status,
                            now,
                            now,
                            probeOk ? now : null,
                            probeOk ? null : now,
                            probeOk ? null : probeRes.getMessage(),
                            probeRes.getLatencyMs(),
                            probeRes.getBodyPreview());
                } else {
                    Long hid = ((Number) health.get("id")).longValue();
                    jdbcTemplate.update(
                            "UPDATE t_resource_runtime_policy SET health_status = ?, last_check_time = ?, last_probe_at = ?, "
                                    + "last_success_at = ?, last_failure_at = ?, last_failure_reason = ?, consecutive_success = ?, "
                                    + "consecutive_failure = ?, probe_latency_ms = ?, probe_payload_summary = ?, check_url = ?, "
                                    + "check_type = ?, healthy_threshold = ? WHERE id = ?",
                            status,
                            now,
                            now,
                            probeOk ? now : prevLastSuccessAt,
                            probeOk ? prevLastFailureAt : now,
                            probeOk ? null : probeRes.getMessage(),
                            probeOk ? prevSuccess + 1L : 0L,
                            probeOk ? 0L : prevFailure + 1L,
                            probeRes.getLatencyMs(),
                            probeRes.getBodyPreview(),
                            endpoint.trim(),
                            CHECK_TYPE_MCP,
                            failThreshold,
                            hid);
                }
                if ("healthy".equalsIgnoreCase(status)) {
                    resourceCircuitHealthBridge.resetOpenOrHalfOpenAfterHealthyProbe(TYPE_MCP, resourceId);
                }
                resourceHealthService.refreshCallability(resourceId);
                String typeCol = valueOf(row.get("resource_type"));
                if (!normHealthStatus(prevProbeStatus).equals(normHealthStatus(status))) {
                    realtimePushService.pushHealthProbeStatusChanged(
                            resourceId,
                            TYPE_MCP.equalsIgnoreCase(typeCol) ? TYPE_MCP : typeCol,
                            resourceCode,
                            StringUtils.hasText(displayName) ? displayName : resourceCode,
                            CHECK_TYPE_MCP,
                            status,
                            prevProbeStatus,
                            now);
                }
            }
            if (!targets.isEmpty()) {
                log.info("[定时任务] {} 完成: MCP {} 个 (跳过: {}, 健康: {}, 降级: {}, 下线: {})",
                        TASK_NAME, targets.size(), skipped, ok, degraded, down);
            }
        } catch (DataAccessException e) {
            log.warn("[定时任务] {} 失败: {}", TASK_NAME, e.getMessage());
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }

    private static String resolveTransport(Map<String, Object> authConfig, String protocolCol, String endpoint) {
        if (authConfig != null) {
            Object t = authConfig.get("transport");
            if (t != null && StringUtils.hasText(String.valueOf(t).trim())) {
                return String.valueOf(t).trim();
            }
        }
        if (protocolCol != null && "websocket".equalsIgnoreCase(protocolCol.trim())) {
            return "websocket";
        }
        String e = endpoint.trim().toLowerCase(Locale.ROOT);
        if (e.startsWith("ws://") || e.startsWith("wss://")) {
            return "websocket";
        }
        return null;
    }

    private Map<String, Object> parseJsonMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        try {
            if (raw instanceof String s) {
                if (!StringUtils.hasText(s)) {
                    return Map.of();
                }
                return objectMapper.readValue(s, new TypeReference<>() {
                });
            }
            return objectMapper.convertValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Map.of();
        }
    }

    private static String trimToNull(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String valueOf(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String normHealthStatus(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static long longValue(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
        } catch (Exception ex) {
            return null;
        }
    }
}
