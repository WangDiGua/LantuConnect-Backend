package com.lantu.connect.monitoring.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.dto.McpConnectivityProbeRequest;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.service.GatewayBindingExpansionService;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
import com.lantu.connect.monitoring.ResourceCircuitHealthBridge;
import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.realtime.RealtimePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 统一资源健康治理实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceHealthServiceImpl implements ResourceHealthService {

    private static final String TYPE_AGENT = "agent";
    private static final String TYPE_SKILL = "skill";
    private static final String TYPE_MCP = "mcp";
    private static final String CALLABLE = "callable";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
    private final McpConnectivityProbeService mcpConnectivityProbeService;
    private final GatewayBindingExpansionService gatewayBindingExpansionService;
    private final ResourceCircuitHealthBridge resourceCircuitHealthBridge;
    private final RealtimePushService realtimePushService;

    @Override
    public void ensurePolicyForResource(Long resourceId) {
        ResourceRow row = loadResourceRow(resourceId);
        if (row == null) {
            return;
        }
        String probeStrategy = defaultProbeStrategy(row.resourceType, row.protocol);
        String checkType = defaultCheckType(row.resourceType, row.protocol);
        String checkUrl = defaultCheckUrl(row.resourceType, row.upstreamEndpoint, row.endpoint);
        String healthStatus = normalizeHealthStatus(row.healthStatus);
        String circuitState = normalizeCircuitState(row.currentState);
        String callabilityState = computeCallabilityState(row, healthStatus, circuitState);
        String callabilityReason = computeCallabilityReason(row, healthStatus, circuitState, callabilityState);
        LocalDateTime now = LocalDateTime.now();
        if (row.policyId == null) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_runtime_policy (
                                resource_id, resource_type, resource_code, display_name,
                                check_type, check_url, probe_strategy,
                                interval_sec, healthy_threshold, timeout_sec,
                                health_status, current_state, callability_state, callability_reason,
                                consecutive_success, consecutive_failure,
                                last_probe_at, last_success_at, last_failure_at, last_failure_reason,
                                probe_latency_ms, probe_payload_summary,
                                create_time, update_time
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, NULL, NULL, NULL, NULL, NULL, NULL, ?, ?)
                            """,
                    row.resourceId, row.resourceType, row.resourceCode, row.displayName,
                    checkType, checkUrl, probeStrategy,
                    defaultInterval(row.resourceType), defaultHealthyThreshold(row.resourceType), defaultTimeout(row.resourceType),
                    healthStatus, circuitState, callabilityState, callabilityReason,
                    now, now);
            return;
        }
        jdbcTemplate.update("""
                        UPDATE t_resource_runtime_policy
                        SET resource_type = ?,
                            resource_code = ?,
                            display_name = ?,
                            check_type = COALESCE(NULLIF(TRIM(?), ''), check_type),
                            check_url = COALESCE(NULLIF(TRIM(?), ''), check_url),
                            probe_strategy = COALESCE(NULLIF(TRIM(?), ''), probe_strategy),
                            health_status = COALESCE(NULLIF(TRIM(?), ''), health_status),
                            current_state = COALESCE(NULLIF(TRIM(?), ''), current_state),
                            callability_state = ?,
                            callability_reason = ?,
                            update_time = NOW()
                        WHERE id = ?
                        """,
                row.resourceType, row.resourceCode, row.displayName,
                checkType, checkUrl, probeStrategy,
                healthStatus, circuitState, callabilityState, callabilityReason,
                row.policyId);
    }

    @Override
    public ResourceHealthSnapshotVO probeAndPersist(Long resourceId) {
        ResourceRow row = loadResourceRow(resourceId);
        if (row == null) {
            return null;
        }
        ensurePolicyForResource(resourceId);
        row = loadResourceRow(resourceId);
        if (row == null) {
            return null;
        }
        ProbeOutcome outcome = switch (row.resourceType) {
            case TYPE_AGENT -> probeAgent(row);
            case TYPE_SKILL -> probeSkill(row);
            case TYPE_MCP -> probeMcp(row);
            default -> ProbeOutcome.down("不支持的资源类型", "resourceType=" + row.resourceType, 0L, null);
        };
        persistProbeOutcome(row, outcome);
        if (CALLABLE.equalsIgnoreCase(outcome.callabilityState)) {
            resourceCircuitHealthBridge.resetOpenOrHalfOpenAfterHealthyProbe(row.resourceType, row.resourceId);
        } else if ("down".equalsIgnoreCase(outcome.healthStatus)) {
            openCircuitForProbeFailure(row.resourceType, row.resourceId);
        }
        ResourceHealthSnapshotVO snapshot = getSnapshot(resourceId);
        pushSnapshotChanged(snapshot);
        return snapshot;
    }

    @Override
    public ResourceHealthSnapshotVO manualBreak(Long resourceId, Integer openDurationSeconds) {
        ResourceRow row = loadResourceRow(resourceId);
        if (row == null) {
            return null;
        }
        ensurePolicyForResource(resourceId);
        jdbcTemplate.update("""
                        UPDATE t_resource_runtime_policy
                        SET current_state = 'OPEN',
                            callability_state = 'circuit_open',
                            callability_reason = ?,
                            last_opened_at = COALESCE(last_opened_at, NOW()),
                            open_duration_sec = ?,
                            update_time = NOW()
                        WHERE resource_id = ?
                        """,
                "手动熔断：资源已被管理端临时关闭",
                Math.max(5, openDurationSeconds == null ? 60 : openDurationSeconds),
                resourceId);
        ResourceHealthSnapshotVO snapshot = refreshCallability(resourceId);
        pushSnapshotChanged(snapshot);
        return snapshot;
    }

    @Override
    public ResourceHealthSnapshotVO manualRecover(Long resourceId) {
        ResourceRow row = loadResourceRow(resourceId);
        if (row == null) {
            return null;
        }
        ensurePolicyForResource(resourceId);
        jdbcTemplate.update("""
                        UPDATE t_resource_runtime_policy
                        SET current_state = 'CLOSED',
                            callability_state = 'callable',
                            callability_reason = NULL,
                            failure_count = 0,
                            success_count = 0,
                            update_time = NOW()
                        WHERE resource_id = ?
                        """,
                resourceId);
        ResourceHealthSnapshotVO snapshot = refreshCallability(resourceId);
        pushSnapshotChanged(snapshot);
        return snapshot;
    }

    @Override
    public ResourceHealthSnapshotVO refreshCallability(Long resourceId) {
        ResourceRow row = loadResourceRow(resourceId);
        if (row == null) {
            return null;
        }
        String healthStatus = normalizeHealthStatus(row.healthStatus);
        String circuitState = normalizeCircuitState(row.currentState);
        String callabilityState = computeCallabilityState(row, healthStatus, circuitState);
        String callabilityReason = computeCallabilityReason(row, healthStatus, circuitState, callabilityState);
        jdbcTemplate.update("""
                        UPDATE t_resource_runtime_policy
                        SET callability_state = ?, callability_reason = ?, update_time = NOW()
                        WHERE resource_id = ?
                        """,
                callabilityState, callabilityReason, resourceId);
        ResourceHealthSnapshotVO snapshot = getSnapshot(resourceId);
        pushSnapshotChanged(snapshot);
        return snapshot;
    }

    @Override
    public ResourceHealthSnapshotVO getSnapshot(Long resourceId) {
        ResourceRow row = loadResourceRow(resourceId);
        return row == null ? null : toSnapshot(row);
    }

    @Override
    public List<ResourceHealthSnapshotVO> listSnapshots(String resourceType, String healthStatus, String callabilityState) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.status,
                       p.id AS policy_id, p.health_status, p.current_state, p.callability_state, p.callability_reason,
                       p.check_type, p.check_url, p.probe_strategy,
                       p.last_probe_at, p.last_success_at, p.last_failure_at, p.last_failure_reason,
                       p.consecutive_success, p.consecutive_failure, p.probe_latency_ms, p.probe_payload_summary,
                       p.interval_sec, p.healthy_threshold, p.timeout_sec
                FROM t_resource r
                LEFT JOIN t_resource_runtime_policy p ON p.resource_id = r.id
                WHERE r.deleted = 0
                """);
        if (StringUtils.hasText(resourceType)) {
            sql.append(" AND r.resource_type = ? ");
            args.add(resourceType.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(healthStatus)) {
            sql.append(" AND p.health_status = ? ");
            args.add(healthStatus.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(callabilityState)) {
            sql.append(" AND p.callability_state = ? ");
            args.add(callabilityState.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY r.update_time DESC, r.id DESC ");
        return jdbcTemplate.query(sql.toString(), (rs, i) -> ResourceHealthSnapshotVO.builder()
                .resourceId(rs.getLong("id"))
                .resourceType(rs.getString("resource_type"))
                .resourceCode(rs.getString("resource_code"))
                .displayName(rs.getString("display_name"))
                .resourceStatus(rs.getString("status"))
                .healthStatus(rs.getString("health_status"))
                .circuitState(rs.getString("current_state"))
                .callabilityState(rs.getString("callability_state"))
                .callabilityReason(rs.getString("callability_reason"))
                .callable(CALLABLE.equalsIgnoreCase(rs.getString("callability_state")))
                .checkType(rs.getString("check_type"))
                .checkUrl(rs.getString("check_url"))
                .probeStrategy(rs.getString("probe_strategy"))
                .lastProbeAt(rs.getTimestamp("last_probe_at") == null ? null : rs.getTimestamp("last_probe_at").toLocalDateTime())
                .lastSuccessAt(rs.getTimestamp("last_success_at") == null ? null : rs.getTimestamp("last_success_at").toLocalDateTime())
                .lastFailureAt(rs.getTimestamp("last_failure_at") == null ? null : rs.getTimestamp("last_failure_at").toLocalDateTime())
                .lastFailureReason(rs.getString("last_failure_reason"))
                .consecutiveSuccess(longValue(rs.getObject("consecutive_success")))
                .consecutiveFailure(longValue(rs.getObject("consecutive_failure")))
                .probeLatencyMs(longObject(rs.getObject("probe_latency_ms")))
                .probePayloadSummary(rs.getString("probe_payload_summary"))
                .intervalSec(intObject(rs.getObject("interval_sec")))
                .healthyThreshold(intObject(rs.getObject("healthy_threshold")))
                .timeoutSec(intObject(rs.getObject("timeout_sec")))
                .build(), args.toArray());
    }

    private ResourceHealthSnapshotVO toSnapshot(ResourceRow row) {
        String healthStatus = normalizeHealthStatus(row.healthStatus);
        String circuitState = normalizeCircuitState(row.currentState);
        String callabilityState = computeCallabilityState(row, healthStatus, circuitState);
        String callabilityReason = computeCallabilityReason(row, healthStatus, circuitState, callabilityState);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("resourceEnabled", row.resourceEnabled);
        evidence.put("dependencyCount", row.dependencyCount);
        evidence.put("dependencyReason", row.dependencyReason);
        evidence.put("probeOutcome", row.probeOutcome);
        return ResourceHealthSnapshotVO.builder()
                .resourceId(row.resourceId)
                .resourceType(row.resourceType)
                .resourceCode(row.resourceCode)
                .displayName(row.displayName)
                .resourceStatus(row.resourceStatus)
                .probeStrategy(row.probeStrategy)
                .checkType(row.checkType)
                .checkUrl(row.checkUrl)
                .healthStatus(healthStatus)
                .circuitState(circuitState)
                .callabilityState(callabilityState)
                .callabilityReason(callabilityReason)
                .callable(CALLABLE.equalsIgnoreCase(callabilityState))
                .resourceEnabled(row.resourceEnabled)
                .lastProbeAt(row.lastProbeAt)
                .lastSuccessAt(row.lastSuccessAt)
                .lastFailureAt(row.lastFailureAt)
                .lastFailureReason(row.lastFailureReason)
                .consecutiveSuccess(row.consecutiveSuccess)
                .consecutiveFailure(row.consecutiveFailure)
                .probeLatencyMs(row.probeLatencyMs)
                .probePayloadSummary(row.probePayloadSummary)
                .intervalSec(row.intervalSec)
                .healthyThreshold(row.healthyThreshold)
                .timeoutSec(row.timeoutSec)
                .probeEvidence(evidence)
                .build();
    }

    private void persistProbeOutcome(ResourceRow row, ProbeOutcome outcome) {
        boolean healthy = "healthy".equalsIgnoreCase(outcome.healthStatus);
        long nextSuccess = healthy ? row.consecutiveSuccess + 1L : 0L;
        long nextFailure = healthy ? 0L : row.consecutiveFailure + 1L;
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        UPDATE t_resource_runtime_policy
                        SET health_status = ?,
                            callability_state = ?,
                            callability_reason = ?,
                            last_probe_at = ?,
                            last_success_at = ?,
                            last_failure_at = ?,
                            last_failure_reason = ?,
                            consecutive_success = ?,
                            consecutive_failure = ?,
                            probe_latency_ms = ?,
                            probe_payload_summary = ?,
                            update_time = NOW()
                        WHERE resource_id = ?
                        """,
                outcome.healthStatus,
                outcome.callabilityState,
                outcome.callabilityReason,
                now,
                healthy ? now : row.lastSuccessAt,
                healthy ? row.lastFailureAt : now,
                healthy ? null : outcome.failureReason,
                nextSuccess,
                nextFailure,
                outcome.latencyMs,
                outcome.payloadSummary,
                row.resourceId);
    }

    private ResourceRow loadResourceRow(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        List<Map<String, Object>> baseRows = jdbcTemplate.queryForList("""
                SELECT id, resource_type, resource_code, display_name, status
                FROM t_resource
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, resourceId);
        if (baseRows.isEmpty()) {
            return null;
        }
        Map<String, Object> base = baseRows.get(0);
        String resourceType = normalizeType(base.get("resource_type"));
        Map<String, Object> policy = loadPolicy(resourceId);
        Map<String, Object> ext = loadExt(resourceId, resourceType);
        ResourceRow row = new ResourceRow();
        row.resourceId = resourceId;
        row.resourceType = resourceType;
        row.resourceCode = str(base.get("resource_code"));
        row.displayName = str(base.get("display_name"));
        row.resourceStatus = str(base.get("status"));
        row.policyId = policy == null ? null : longObject(policy.get("id"));
        row.healthStatus = policy == null ? null : str(policy.get("health_status"));
        row.currentState = policy == null ? null : str(policy.get("current_state"));
        row.callabilityState = policy == null ? null : str(policy.get("callability_state"));
        row.callabilityReason = policy == null ? null : str(policy.get("callability_reason"));
        row.lastProbeAt = policy == null ? null : toDateTime(policy.get("last_probe_at"));
        row.lastSuccessAt = policy == null ? null : toDateTime(policy.get("last_success_at"));
        row.lastFailureAt = policy == null ? null : toDateTime(policy.get("last_failure_at"));
        row.lastFailureReason = policy == null ? null : str(policy.get("last_failure_reason"));
        row.consecutiveSuccess = policy == null ? 0L : longValue(policy.get("consecutive_success"));
        row.consecutiveFailure = policy == null ? 0L : longValue(policy.get("consecutive_failure"));
        row.probeLatencyMs = policy == null ? null : longObject(policy.get("probe_latency_ms"));
        row.probePayloadSummary = policy == null ? null : str(policy.get("probe_payload_summary"));
        row.intervalSec = policy == null ? defaultInterval(resourceType) : intObject(policy.get("interval_sec"));
        row.healthyThreshold = policy == null ? defaultHealthyThreshold(resourceType) : intObject(policy.get("healthy_threshold"));
        row.timeoutSec = policy == null ? defaultTimeout(resourceType) : intObject(policy.get("timeout_sec"));
        row.checkType = policy == null ? null : str(policy.get("check_type"));
        row.checkUrl = policy == null ? null : str(policy.get("check_url"));
        row.probeStrategy = policy == null ? null : str(policy.get("probe_strategy"));
        row.dependencyCount = dependencyCount(resourceType, resourceId);
        row.dependencyReason = dependencyReason(resourceType, resourceId);
        row.probeOutcome = buildProbeOutcomeSummary(row);
        if (TYPE_AGENT.equalsIgnoreCase(resourceType)) {
            row.resourceEnabled = boolValue(ext.get("enabled"));
            row.registrationProtocol = str(ext.get("registration_protocol"));
            row.upstreamEndpoint = str(ext.get("upstream_endpoint"));
            row.upstreamAgentId = str(ext.get("upstream_agent_id"));
            row.credentialRef = str(ext.get("credential_ref"));
            row.transformProfile = str(ext.get("transform_profile"));
            row.modelAlias = str(ext.get("model_alias"));
        } else if (TYPE_SKILL.equalsIgnoreCase(resourceType)) {
            row.executionMode = str(ext.get("execution_mode"));
            row.contextPrompt = str(ext.get("context_prompt"));
        } else if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            row.endpoint = str(ext.get("endpoint"));
            row.protocol = str(ext.get("protocol"));
            row.authType = str(ext.get("auth_type"));
            row.authConfig = ext.get("auth_config");
        }
        return row;
    }

    private Map<String, Object> loadPolicy(Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT *
                FROM t_resource_runtime_policy
                WHERE resource_id = ?
                LIMIT 1
                """, resourceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> loadExt(Long resourceId, String resourceType) {
        if (!StringUtils.hasText(resourceType)) {
            return Map.of();
        }
        String type = resourceType.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = switch (type) {
            case TYPE_AGENT -> jdbcTemplate.queryForList("""
                    SELECT enabled, registration_protocol, upstream_endpoint, upstream_agent_id,
                           credential_ref, transform_profile, model_alias
                    FROM t_resource_agent_ext
                    WHERE resource_id = ?
                    LIMIT 1
                    """, resourceId);
            case TYPE_SKILL -> jdbcTemplate.queryForList("""
                    SELECT execution_mode, context_prompt
                    FROM t_resource_skill_ext
                    WHERE resource_id = ?
                    LIMIT 1
                    """, resourceId);
            case TYPE_MCP -> jdbcTemplate.queryForList("""
                    SELECT endpoint, protocol, auth_type, auth_config
                    FROM t_resource_mcp_ext
                    WHERE resource_id = ?
                    LIMIT 1
                    """, resourceId);
            default -> List.of();
        };
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private ProbeOutcome probeAgent(ResourceRow row) {
        if (Boolean.FALSE.equals(row.resourceEnabled)) {
            return ProbeOutcome.disabled("Agent 已禁用，跳过探测");
        }
        if (!StringUtils.hasText(row.upstreamEndpoint)) {
            return ProbeOutcome.down("上游地址缺失", "upstreamEndpoint 为空", 0L, null);
        }
        String protocol = StringUtils.hasText(row.registrationProtocol) ? row.registrationProtocol.trim().toLowerCase(Locale.ROOT) : "openai_compatible";
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("registrationProtocol", protocol);
        spec.put("upstreamAgentId", row.upstreamAgentId);
        spec.put("credentialRef", row.credentialRef);
        spec.put("transformProfile", row.transformProfile);
        spec.put("modelAlias", row.modelAlias);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", "健康自检");
        payload.put("_probe", true);
        long t0 = System.nanoTime();
        try {
            ProtocolInvokeResult result = protocolInvokerRegistry.invoke(
                    protocol,
                    row.upstreamEndpoint,
                    defaultTimeout(TYPE_AGENT),
                    "health-" + UUID.randomUUID(),
                    payload,
                    spec,
                    ProtocolInvokeContext.of(null, row.resourceId, null));
            long latencyMs = result.latencyMs() > 0 ? result.latencyMs() : Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
            String body = normalizeBody(result.body());
            if (result.statusCode() >= 200 && result.statusCode() < 300) {
                String depReason = dependencyReason(row.resourceType, row.resourceId);
                if (StringUtils.hasText(depReason)) {
                    return ProbeOutcome.down("依赖 MCP 异常", depReason, latencyMs, body);
                }
                return ProbeOutcome.healthy("Agent 上游联通正常", latencyMs, body);
            }
            if (result.statusCode() == 429) {
                return ProbeOutcome.degraded("上游限流", "agent 上游返回 429", latencyMs, body);
            }
            return ProbeOutcome.down("上游不可用", "agent 上游返回 HTTP " + result.statusCode(), latencyMs, body);
        } catch (Exception ex) {
            long latencyMs = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
            return ProbeOutcome.down("上游探测异常", safeMessage(ex), latencyMs, null);
        }
    }

    private ProbeOutcome probeSkill(ResourceRow row) {
        if (!StringUtils.hasText(row.contextPrompt)) {
            return ProbeOutcome.down("技能提示词缺失", "contextPrompt 为空", 0L, null);
        }
        if (!"context".equalsIgnoreCase(StringUtils.hasText(row.executionMode) ? row.executionMode.trim() : "context")) {
            return ProbeOutcome.down("执行模式异常", "executionMode 必须为 context", 0L, null);
        }
        String depReason = dependencyReason(row.resourceType, row.resourceId);
        if (StringUtils.hasText(depReason)) {
            return ProbeOutcome.down("依赖 MCP 异常", depReason, 0L, depReason);
        }
        return ProbeOutcome.healthy("Skill 元数据与依赖正常", 0L, "mcpBindings=ok");
    }

    private ProbeOutcome probeMcp(ResourceRow row) {
        if (!StringUtils.hasText(row.endpoint)) {
            return ProbeOutcome.down("MCP endpoint 缺失", "endpoint 为空", 0L, null);
        }
        if ("stdio".equalsIgnoreCase(row.protocol)) {
            return ProbeOutcome.down("stdio 资源待宿主探测", "stdio MCP 暂不支持平台主动连通性探测", 0L, "protocol=stdio");
        }
        McpConnectivityProbeRequest probeReq = new McpConnectivityProbeRequest();
        probeReq.setEndpoint(row.endpoint);
        probeReq.setAuthType(row.authType);
        probeReq.setAuthConfig(parseMap(row.authConfig));
        String transport = resolveTransport(row.protocol, row.endpoint, probeReq.getAuthConfig());
        if (StringUtils.hasText(transport)) {
            probeReq.setTransport(transport);
        }
        long t0 = System.nanoTime();
        try {
            McpConnectivityProbeResult res = mcpConnectivityProbeService.probe(probeReq);
            long latencyMs = res.getLatencyMs() > 0 ? res.getLatencyMs() : Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
            String summary = normalizeBody(res.getBodyPreview());
            if (res.isOk()) {
                return ProbeOutcome.healthy("MCP 连接与认证正常", latencyMs, summary);
            }
            return res.getStatusCode() == 429
                    ? ProbeOutcome.degraded("MCP 限流", res.getMessage(), latencyMs, summary)
                    : ProbeOutcome.down("MCP 探测失败", res.getMessage(), latencyMs, summary);
        } catch (Exception ex) {
            long latencyMs = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
            return ProbeOutcome.down("MCP 探测异常", safeMessage(ex), latencyMs, null);
        }
    }

    private String defaultProbeStrategy(String resourceType, String protocol) {
        if (TYPE_AGENT.equalsIgnoreCase(resourceType)) {
            return "agent_provider";
        }
        if (TYPE_SKILL.equalsIgnoreCase(resourceType)) {
            return "skill_dependency";
        }
        if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            return "stdio".equalsIgnoreCase(protocol) ? "mcp_stdio" : "mcp_jsonrpc";
        }
        return "http";
    }

    private String defaultCheckType(String resourceType, String protocol) {
        if (TYPE_AGENT.equalsIgnoreCase(resourceType)) {
            return "provider";
        }
        if (TYPE_SKILL.equalsIgnoreCase(resourceType)) {
            return "dependency";
        }
        if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            return "stdio".equalsIgnoreCase(protocol) ? "stdio" : "mcp_jsonrpc";
        }
        return "http";
    }

    private String defaultCheckUrl(String resourceType, String upstreamEndpoint, String endpoint) {
        if (TYPE_AGENT.equalsIgnoreCase(resourceType)) {
            return upstreamEndpoint;
        }
        if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            return endpoint;
        }
        return null;
    }

    private int defaultInterval(String resourceType) {
        if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            return 300;
        }
        if (TYPE_SKILL.equalsIgnoreCase(resourceType)) {
            return 120;
        }
        return 60;
    }

    private int defaultHealthyThreshold(String resourceType) {
        return TYPE_MCP.equalsIgnoreCase(resourceType) ? 3 : 2;
    }

    private int defaultTimeout(String resourceType) {
        if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            return 20;
        }
        if (TYPE_AGENT.equalsIgnoreCase(resourceType)) {
            return 15;
        }
        return 10;
    }

    private String computeCallabilityState(ResourceRow row, String healthStatus, String circuitState) {
        if (row == null) {
            return "not_configured";
        }
        if (!StringUtils.hasText(row.resourceStatus) || !"published".equalsIgnoreCase(row.resourceStatus)) {
            return "not_published";
        }
        if (Boolean.FALSE.equals(row.resourceEnabled)) {
            return "disabled";
        }
        if ("OPEN".equalsIgnoreCase(circuitState)) {
            return "circuit_open";
        }
        if ("HALF_OPEN".equalsIgnoreCase(circuitState)) {
            return "circuit_half_open";
        }
        if ("down".equalsIgnoreCase(healthStatus)) {
            return "health_down";
        }
        if ("degraded".equalsIgnoreCase(healthStatus)) {
            return "health_degraded";
        }
        if ("disabled".equalsIgnoreCase(healthStatus)) {
            return "disabled";
        }
        if (StringUtils.hasText(dependencyReason(row.resourceType, row.resourceId))) {
            return "dependency_blocked";
        }
        return CALLABLE;
    }

    private String computeCallabilityReason(ResourceRow row, String healthStatus, String circuitState, String callabilityState) {
        return switch (callabilityState) {
            case CALLABLE -> "资源健康且熔断闭合，可分发";
            case "not_published" -> "资源尚未发布，禁止对外调用";
            case "disabled" -> "资源已禁用或健康检查被关闭";
            case "circuit_open" -> "熔断已打开，当前禁止调用";
            case "circuit_half_open" -> "熔断处于半开态，等待恢复探测";
            case "health_down" -> StringUtils.hasText(row.lastFailureReason) ? row.lastFailureReason : "健康探测判定为 down";
            case "health_degraded" -> StringUtils.hasText(row.lastFailureReason) ? row.lastFailureReason : "健康探测判定为 degraded";
            case "dependency_blocked" -> dependencyReason(row.resourceType, row.resourceId);
            default -> "资源暂不可用";
        };
    }

    private long dependencyCount(String resourceType, Long resourceId) {
        return dependencyIds(resourceType, resourceId).size();
    }

    private String dependencyReason(String resourceType, Long resourceId) {
        List<Long> ids = dependencyIds(resourceType, resourceId);
        if (ids.isEmpty()) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        for (Long id : ids) {
            ResourceHealthSnapshotVO dep = getSnapshot(id);
            if (dep == null) {
                reasons.add("MCP " + id + " 不存在");
            } else if (!Boolean.TRUE.equals(dep.getCallable())) {
                reasons.add("MCP " + dep.getResourceCode() + " (" + dep.getCallabilityState() + ")");
            }
        }
        return reasons.isEmpty() ? null : String.join("；", reasons);
    }

    private List<Long> dependencyIds(String resourceType, Long resourceId) {
        if (!StringUtils.hasText(resourceType) || resourceId == null) {
            return List.of();
        }
        String type = resourceType.trim().toLowerCase(Locale.ROOT);
        if (TYPE_AGENT.equals(type)) {
            return gatewayBindingExpansionService.listAgentBoundMcpIds(resourceId);
        }
        if (TYPE_SKILL.equals(type)) {
            return gatewayBindingExpansionService.listSkillBoundMcpIds(resourceId);
        }
        return List.of();
    }

    private void openCircuitForProbeFailure(String resourceType, Long resourceId) {
        if (!StringUtils.hasText(resourceType) || resourceId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE t_resource_runtime_policy
                        SET current_state = 'OPEN', last_opened_at = COALESCE(last_opened_at, NOW()), update_time = NOW()
                        WHERE resource_type = ? AND resource_id = ?
                        """,
                resourceType.trim().toLowerCase(Locale.ROOT), resourceId);
        refreshCallability(resourceId);
    }

    private void pushSnapshotChanged(ResourceHealthSnapshotVO snapshot) {
        if (snapshot == null || snapshot.getResourceId() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", snapshot.getResourceId());
        payload.put("resourceType", snapshot.getResourceType());
        payload.put("resourceCode", snapshot.getResourceCode());
        payload.put("displayName", snapshot.getDisplayName());
        payload.put("healthStatus", snapshot.getHealthStatus());
        payload.put("circuitState", snapshot.getCircuitState());
        payload.put("callabilityState", snapshot.getCallabilityState());
        payload.put("callabilityReason", snapshot.getCallabilityReason());
        payload.put("lastFailureReason", snapshot.getLastFailureReason());
        payload.put("consecutiveFailure", snapshot.getConsecutiveFailure());
        payload.put("probeStrategy", snapshot.getProbeStrategy());
        payload.put("lastProbeAt", snapshot.getLastProbeAt() != null ? snapshot.getLastProbeAt().toString() : null);
        log.debug("health snapshot changed: {}", payload);
        realtimePushService.getClass();
    }

    private Map<String, Object> parseMap(Object raw) {
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
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String normalizeType(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(s) ? s : null;
    }

    private static String normalizeToken(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return StringUtils.hasText(s) ? s : null;
    }

    private static String normalizeHealthStatus(Object raw) {
        if (raw == null) {
            return "unknown";
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(s) ? s.replace('-', '_') : "unknown";
    }

    private static String normalizeCircuitState(Object raw) {
        if (raw == null) {
            return "CLOSED";
        }
        String s = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        return StringUtils.hasText(s) ? s : "CLOSED";
    }

    private static String normalizeBody(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.length() > 1024 ? raw.substring(0, 1021) + "..." : raw;
    }

    private static String resolveTransport(String protocol, String endpoint, Map<String, Object> authConfig) {
        if (authConfig != null) {
            Object t = authConfig.get("transport");
            if (t != null && StringUtils.hasText(String.valueOf(t))) {
                return String.valueOf(t).trim().toLowerCase(Locale.ROOT);
            }
        }
        if ("websocket".equalsIgnoreCase(protocol)) {
            return "websocket";
        }
        if (StringUtils.hasText(endpoint)) {
            String e = endpoint.trim().toLowerCase(Locale.ROOT);
            if (e.startsWith("ws://") || e.startsWith("wss://")) {
                return "websocket";
            }
        }
        return null;
    }

    private static String safeMessage(Throwable ex) {
        if (ex == null) {
            return "unknown";
        }
        String msg = ex.getMessage();
        if (!StringUtils.hasText(msg)) {
            msg = ex.getClass().getSimpleName();
        }
        return msg.length() > 512 ? msg.substring(0, 509) + "..." : msg;
    }

    private static long longValue(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return raw == null ? 0L : Long.parseLong(String.valueOf(raw));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static Long longObject(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return raw == null ? null : Long.valueOf(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer intObject(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return raw == null ? null : Integer.valueOf(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Boolean boolValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(raw).trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }

    private static LocalDateTime toDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt;
        }
        try {
            return LocalDateTime.parse(String.valueOf(raw).replace(' ', 'T'));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static String buildProbeOutcomeSummary(ResourceRow row) {
        if (row == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resourceType", row.resourceType);
        summary.put("resourceEnabled", row.resourceEnabled);
        summary.put("dependencyCount", row.dependencyCount);
        summary.put("dependencyReason", row.dependencyReason);
        summary.put("probeStrategy", row.probeStrategy);
        return String.valueOf(summary);
    }

    private static final class ResourceRow {
        Long resourceId;
        String resourceType;
        String resourceCode;
        String displayName;
        String resourceStatus;
        Long policyId;
        String healthStatus;
        String currentState;
        String callabilityState;
        String callabilityReason;
        LocalDateTime lastProbeAt;
        LocalDateTime lastSuccessAt;
        LocalDateTime lastFailureAt;
        String lastFailureReason;
        Long consecutiveSuccess;
        Long consecutiveFailure;
        Long probeLatencyMs;
        String probePayloadSummary;
        Integer intervalSec;
        Integer healthyThreshold;
        Integer timeoutSec;
        String checkType;
        String checkUrl;
        String probeStrategy;
        Boolean resourceEnabled;
        String registrationProtocol;
        String upstreamEndpoint;
        String upstreamAgentId;
        String credentialRef;
        String transformProfile;
        String modelAlias;
        String executionMode;
        String contextPrompt;
        String endpoint;
        String protocol;
        String authType;
        Object authConfig;
        long dependencyCount;
        String dependencyReason;
        String probeOutcome;
    }

    private record ProbeOutcome(String healthStatus, String callabilityState, String callabilityReason,
                                String failureReason, Long latencyMs, String payloadSummary) {
        static ProbeOutcome healthy(String reason, long latencyMs, String payloadSummary) {
            return new ProbeOutcome("healthy", CALLABLE, reason, null, latencyMs, payloadSummary);
        }

        static ProbeOutcome degraded(String reason, String failureReason, long latencyMs, String payloadSummary) {
            return new ProbeOutcome("degraded", "dependency_blocked", reason, failureReason, latencyMs, payloadSummary);
        }

        static ProbeOutcome down(String reason, String failureReason, long latencyMs, String payloadSummary) {
            return new ProbeOutcome("down", "health_down", reason, failureReason, latencyMs, payloadSummary);
        }

        static ProbeOutcome disabled(String reason) {
            return new ProbeOutcome("disabled", "disabled", reason, reason, 0L, null);
        }
    }
}
