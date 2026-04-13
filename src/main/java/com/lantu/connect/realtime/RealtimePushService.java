package com.lantu.connect.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.config.RealtimePushProperties;
import com.lantu.connect.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 浏览器 WebSocket 推送：站内通知、健康/熔断治理、告警、待审队列与监控 KPI 等，统一 v1 JSON 协议。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimePushService {

    private static final int PROTOCOL_VERSION = 1;

    private final UserWebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final RealtimePushRecipientResolver recipientResolver;
    private final RealtimePushProperties realtimePushProperties;
    private final JdbcTemplate jdbcTemplate;

    public void pushNotificationCreated(Long userId, Notification n, long unreadCount) {
        if (userId == null || n == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "notification");
        body.put("action", "created");
        body.put("notification", toPayload(n));
        body.put("unreadCount", unreadCount);
        sendToUser(userId, body);
    }

    public void pushUnreadSync(Long userId, long unreadCount) {
        if (userId == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "notification");
        body.put("action", "unread_sync");
        body.put("unreadCount", unreadCount);
        sendToUser(userId, body);
    }

    /**
     * 管理端保存健康配置、启用/停用探活等。
     */
    public void pushHealthConfigChanged(
            Long resourceId,
            String agentType,
            String agentName,
            String displayName,
            String checkType,
            String healthStatus,
            LocalDateTime updatedAt,
            String previousStatus) {
        if (!realtimePushProperties.isPushHealth() || resourceId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", resourceId);
        payload.put("resourceType", agentType);
        payload.put("resourceCode", agentName);
        payload.put("displayName", displayName);
        payload.put("checkType", checkType);
        payload.put("healthStatus", healthStatus);
        payload.put("previousStatus", previousStatus);
        payload.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "health");
        body.put("action", "config_updated");
        body.put("payload", payload);
        broadcast(recipientResolver.resolveGovernanceRecipients(resourceId), body);
    }

    /**
     * 定时探活（HTTP / MCP）写入 {@code health_status} 后的运行时状态变化。
     */
    public void pushHealthProbeStatusChanged(
            Long resourceId,
            String resourceType,
            String resourceCode,
            String displayName,
            String checkType,
            String newStatus,
            String previousStatus,
            LocalDateTime checkedAt) {
        if (!realtimePushProperties.isPushHealth() || resourceId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", resourceId);
        payload.put("resourceType", resourceType);
        payload.put("resourceCode", resourceCode);
        payload.put("displayName", displayName);
        payload.put("checkType", checkType);
        payload.put("healthStatus", newStatus);
        payload.put("previousStatus", previousStatus);
        payload.put("checkedAt", checkedAt != null ? checkedAt.toString() : null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "health");
        body.put("action", "probe_status_changed");
        body.put("payload", payload);
        broadcast(recipientResolver.resolveGovernanceRecipients(resourceId), body);
    }

    public void pushCircuitStateChanged(
            Long resourceId,
            String resourceType,
            String agentName,
            String displayName,
            String newState,
            String previousState) {
        if (!realtimePushProperties.isPushCircuit() || resourceId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", resourceId);
        payload.put("resourceType", resourceType);
        payload.put("resourceCode", agentName);
        payload.put("displayName", displayName);
        payload.put("newState", newState);
        payload.put("previousState", previousState);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "circuit");
        body.put("action", "state_changed");
        body.put("payload", payload);
        broadcast(recipientResolver.resolveGovernanceRecipients(resourceId), body);
    }

    public void pushAlertFiring(String ruleId, String ruleName, String severity, String recordId, String message) {
        if (!realtimePushProperties.isPushAlert()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", ruleId);
        payload.put("ruleName", ruleName);
        payload.put("severity", severity);
        payload.put("recordId", recordId);
        payload.put("message", message);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "alert");
        body.put("action", "firing");
        body.put("payload", payload);
        broadcast(recipientResolver.resolveGlobalOpsRecipients(), body);
    }

    /**
     * 由 {@link AuditPendingPushDebouncer} 调用：查询当前待审数量并推送。
     */
    public void pushAuditPendingDigest() {
        if (!realtimePushProperties.isPushAudit()) {
            return;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_audit_item WHERE status = ?", Long.class, "pending_review");
        long n = count == null ? 0L : count;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pendingCount", n);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "audit");
        body.put("action", "pending_changed");
        body.put("payload", payload);
        broadcast(recipientResolver.resolveAuditDigestRecipients(), body);
    }

    /**
     * 监控 KPI 摘要（由调度任务或聚合服务调用）；与告警共用全局运维收件人过滤。
     */
    public void pushMonitoringKpiDigest(Map<String, Object> metricsPayload) {
        if (!realtimePushProperties.isPushMonitoringKpi()) {
            return;
        }
        Map<String, Object> payload = metricsPayload != null ? new LinkedHashMap<>(metricsPayload) : new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "monitoring");
        body.put("action", "kpi_digest");
        body.put("payload", payload);
        broadcast(recipientResolver.resolveGlobalOpsRecipients(), body);
    }

    private void sendToUser(Long userId, Map<String, Object> body) {
        try {
            registry.sendText(userId, objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            log.warn("实时推送序列化失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private void broadcast(Set<Long> userIds, Map<String, Object> body) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        final String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.warn("实时推送序列化失败(broadcast): {}", e.getMessage());
            return;
        }
        for (Long uid : userIds) {
            if (uid != null && uid > 0) {
                registry.sendText(uid, json);
            }
        }
    }

    private static Map<String, Object> toPayload(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("userId", n.getUserId());
        m.put("type", n.getType());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("sourceType", n.getSourceType());
        m.put("sourceId", n.getSourceId());
        m.put("isRead", n.getIsRead());
        m.put("category", n.getCategory());
        m.put("severity", n.getSeverity());
        m.put("aggregateKey", n.getAggregateKey());
        m.put("flowStatus", n.getFlowStatus());
        m.put("currentStep", n.getCurrentStep());
        m.put("totalSteps", n.getTotalSteps());
        m.put("stepsJson", n.getStepsJson());
        m.put("actionLabel", n.getActionLabel());
        m.put("actionUrl", n.getActionUrl());
        m.put("metadataJson", n.getMetadataJson());
        m.put("lastEventTime", n.getLastEventTime());
        m.put("updateTime", n.getUpdateTime());
        m.put("createTime", n.getCreateTime());
        return m;
    }
}
