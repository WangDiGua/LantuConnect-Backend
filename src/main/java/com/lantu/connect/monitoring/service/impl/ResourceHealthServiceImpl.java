package com.lantu.connect.monitoring.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.service.GatewayBindingExpansionService;
import com.lantu.connect.monitoring.ResourceCircuitHealthBridge;
import com.lantu.connect.monitoring.dto.ResourceHealthDependencyVO;
import com.lantu.connect.monitoring.dto.ResourceHealthPolicyUpdateRequest;
import com.lantu.connect.monitoring.dto.ResourceHealthPolicyVO;
import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;
import com.lantu.connect.monitoring.probe.ResourceProbeEngine;
import com.lantu.connect.monitoring.probe.ResourceProbeResult;
import com.lantu.connect.monitoring.probe.ResourceProbeTarget;
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
    private final GatewayBindingExpansionService gatewayBindingExpansionService;
    private final ResourceCircuitHealthBridge resourceCircuitHealthBridge;
    private final RealtimePushService realtimePushService;
    private final ResourceProbeEngine resourceProbeEngine;

    @Override
    public void ensurePolicyForResource(Long resourceId) {
        ResourceRow row = loadResourceRow(resourceId);
        if (row == null) {
            return;
        }
        String probeStrategy = defaultProbeStrategy(row.resourceType, row.protocol);
        String checkType = defaultCheckType(row.resourceType, row.protocol);
        String checkUrl = defaultCheckUrl(row.resourceType, row.upstreamEndpoint, row.endpoint);
        Map<String, Object> probeConfig = row.probeConfig == null || row.probeConfig.isEmpty()
                ? defaultProbeConfig(row.resourceType, row.protocol)
                : row.probeConfig;
        Map<String, Object> canaryPayload = row.canaryPayload == null || row.canaryPayload.isEmpty()
                ? defaultCanaryPayload(row)
                : row.canaryPayload;
        String healthStatus = normalizeHealthStatus(row.healthStatus);
        String circuitState = normalizeCircuitState(row.currentState);
        String callabilityState = computeCallabilityState(row, healthStatus, circuitState);
        String callabilityReason = computeCallabilityReason(row, healthStatus, circuitState, callabilityState, row.lastFailureReason);
        if (row.policyId == null) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_runtime_policy (
                                resource_id, resource_type, resource_code, display_name,
                                check_type, check_url, probe_strategy, probe_config_json, canary_payload_json,
                                interval_sec, healthy_threshold, timeout_sec, health_status,
                                current_state, callability_state, callability_reason,
                                failure_threshold, open_duration_sec, half_open_max_calls,
                                consecutive_success, consecutive_failure, create_time, update_time
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, NOW(), NOW())
                            """,
                    row.resourceId,
                    row.resourceType,
                    row.resourceCode,
                    row.displayName,
                    checkType,
                    checkUrl,
                    probeStrategy,
                    writeJson(probeConfig),
                    writeJson(canaryPayload),
                    defaultInterval(row.resourceType),
                    defaultHealthyThreshold(row.resourceType),
                    defaultTimeout(row.resourceType),
                    healthStatus,
                    circuitState,
                    callabilityState,
                    callabilityReason,
                    row.failureThreshold == null ? 5 : row.failureThreshold,
                    row.openDurationSec == null ? 60 : row.openDurationSec,
                    row.halfOpenMaxCalls == null ? 3 : row.halfOpenMaxCalls);
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
                            probe_config_json = COALESCE(?, probe_config_json),
                            canary_payload_json = COALESCE(?, canary_payload_json),
                            callability_state = ?,
                            callability_reason = ?,
                            update_time = NOW()
                        WHERE id = ?
                        """,
                row.resourceType,
                row.resourceCode,
                row.displayName,
                checkType,
                checkUrl,
                probeStrategy,
                writeJson(probeConfig),
                writeJson(canaryPayload),
                callabilityState,
                callabilityReason,
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
        ResourceProbeResult result = resourceProbeEngine.probe(toProbeTarget(row));
        persistProbeOutcome(row, result);
        ResourceHealthSnapshotVO snapshot = getSnapshot(resourceId);
        if (snapshot != null && Boolean.TRUE.equals(snapshot.getCallable())) {
            resourceCircuitHealthBridge.resetOpenOrHalfOpenAfterHealthyProbe(row.resourceType, row.resourceId);
        } else if (snapshot != null && "down".equalsIgnoreCase(snapshot.getHealthStatus())) {
            openCircuitForProbeFailure(row.resourceType, row.resourceId);
            snapshot = getSnapshot(resourceId);
        }
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
                            last_opened_at = NOW(),
                            open_duration_sec = ?,
                            update_time = NOW()
                        WHERE resource_id = ?
                        """,
                "manual circuit open",
                Math.max(5, openDurationSeconds == null ? 60 : openDurationSeconds),
                resourceId);
        ResourceHealthSnapshotVO snapshot = getSnapshot(resourceId);
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
                            failure_count = 0,
                            success_count = 0,
                            update_time = NOW()
                        WHERE resource_id = ?
                        """,
                resourceId);
        return refreshCallability(resourceId);
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
        String callabilityReason = computeCallabilityReason(row, healthStatus, circuitState, callabilityState, row.lastFailureReason);
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
    public ResourceHealthSnapshotVO updatePolicy(Long resourceId, ResourceHealthPolicyUpdateRequest request) {
        ResourceRow row = loadResourceRow(resourceId);
        if (row == null) {
            return null;
        }
        ensurePolicyForResource(resourceId);
        jdbcTemplate.update("""
                        UPDATE t_resource_runtime_policy
                        SET interval_sec = COALESCE(?, interval_sec),
                            healthy_threshold = COALESCE(?, healthy_threshold),
                            timeout_sec = COALESCE(?, timeout_sec),
                            failure_threshold = COALESCE(?, failure_threshold),
                            open_duration_sec = COALESCE(?, open_duration_sec),
                            half_open_max_calls = COALESCE(?, half_open_max_calls),
                            fallback_resource_code = CASE
                                WHEN ? IS NULL THEN fallback_resource_code
                                WHEN TRIM(?) = '' THEN NULL
                                ELSE TRIM(?)
                            END,
                            fallback_message = COALESCE(?, fallback_message),
                            probe_config_json = COALESCE(?, probe_config_json),
                            canary_payload_json = COALESCE(?, canary_payload_json),
                            update_time = NOW()
                        WHERE resource_id = ?
                        """,
                request == null ? null : request.getIntervalSec(),
                request == null ? null : request.getHealthyThreshold(),
                request == null ? null : request.getTimeoutSec(),
                request == null ? null : request.getFailureThreshold(),
                request == null ? null : request.getOpenDurationSec(),
                request == null ? null : request.getHalfOpenMaxCalls(),
                request == null ? null : request.getFallbackResourceCode(),
                request == null ? null : request.getFallbackResourceCode(),
                request == null ? null : request.getFallbackResourceCode(),
                request == null ? null : request.getFallbackMessage(),
                request == null ? null : writeJson(request.getProbeConfig()),
                request == null ? null : writeJson(request.getCanaryPayload()),
                resourceId);
        ResourceHealthSnapshotVO snapshot = getSnapshot(resourceId);
        pushSnapshotChanged(snapshot);
        return snapshot;
    }

    @Override
    public List<ResourceHealthSnapshotVO> listSnapshots(String resourceType, String healthStatus, String callabilityState, String probeStrategy) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT r.id
                FROM t_resource r
                LEFT JOIN t_resource_runtime_policy p ON p.resource_id = r.id
                WHERE r.deleted = 0 AND r.resource_type IN ('agent', 'skill', 'mcp')
                """);
        if (StringUtils.hasText(resourceType)) {
            sql.append(" AND r.resource_type = ? ");
            args.add(resourceType.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(healthStatus)) {
            sql.append(" AND p.health_status = ? ");
            args.add(healthStatus.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY r.update_time DESC, r.id DESC ");
        List<Long> ids = jdbcTemplate.query(sql.toString(), (rs, i) -> rs.getLong(1), args.toArray());
        List<ResourceHealthSnapshotVO> out = new ArrayList<>();
        String expectedCallability = StringUtils.hasText(callabilityState)
                ? callabilityState.trim().toLowerCase(Locale.ROOT)
                : null;
        String expectedProbeStrategy = StringUtils.hasText(probeStrategy)
                ? probeStrategy.trim().toLowerCase(Locale.ROOT)
                : null;
        for (Long id : ids) {
            ResourceHealthSnapshotVO snapshot = getSnapshot(id);
            if (snapshot == null) {
                continue;
            }
            if (expectedCallability != null
                    && !expectedCallability.equalsIgnoreCase(firstText(snapshot.getCallabilityState(), ""))) {
                continue;
            }
            if (expectedProbeStrategy != null
                    && !expectedProbeStrategy.equalsIgnoreCase(firstText(snapshot.getProbeStrategy(), ""))) {
                continue;
            }
            out.add(snapshot);
        }
        return out;
    }

    private ResourceHealthSnapshotVO toSnapshot(ResourceRow row) {
        String healthStatus = normalizeHealthStatus(row.healthStatus);
        String circuitState = normalizeCircuitState(row.currentState);
        String callabilityState = computeCallabilityState(row, healthStatus, circuitState);
        String callabilityReason = computeCallabilityReason(row, healthStatus, circuitState, callabilityState, row.lastFailureReason);
        Map<String, Object> probeEvidence = new LinkedHashMap<>();
        probeEvidence.put("resourceEnabled", row.resourceEnabled);
        probeEvidence.put("dependencyCount", row.dependencyCount);
        probeEvidence.put("dependencyReason", row.dependencyReason);
        probeEvidence.put("probeStrategy", row.probeStrategy);
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
                .probeEvidence(probeEvidence)
                .lastProbeEvidence(row.lastProbeEvidence == null ? Map.of() : row.lastProbeEvidence)
                .policy(toPolicy(row))
                .dependencies(row.dependencies)
                .build();
    }

    private ResourceHealthPolicyVO toPolicy(ResourceRow row) {
        return ResourceHealthPolicyVO.builder()
                .checkType(row.checkType)
                .checkUrl(row.checkUrl)
                .probeStrategy(row.probeStrategy)
                .intervalSec(row.intervalSec)
                .healthyThreshold(row.healthyThreshold)
                .timeoutSec(row.timeoutSec)
                .failureThreshold(row.failureThreshold)
                .openDurationSec(row.openDurationSec)
                .halfOpenMaxCalls(row.halfOpenMaxCalls)
                .fallbackResourceCode(row.fallbackResourceCode)
                .fallbackMessage(row.fallbackMessage)
                .probeConfig(row.probeConfig == null ? Map.of() : row.probeConfig)
                .canaryPayload(row.canaryPayload == null ? Map.of() : row.canaryPayload)
                .build();
    }

    private ResourceProbeTarget toProbeTarget(ResourceRow row) {
        return ResourceProbeTarget.builder()
                .resourceId(row.resourceId)
                .resourceType(row.resourceType)
                .resourceCode(row.resourceCode)
                .displayName(row.displayName)
                .registrationProtocol(row.registrationProtocol)
                .upstreamEndpoint(row.upstreamEndpoint)
                .upstreamAgentId(row.upstreamAgentId)
                .credentialRef(row.credentialRef)
                .transformProfile(row.transformProfile)
                .modelAlias(row.modelAlias)
                .executionMode(row.executionMode)
                .contextPrompt(row.contextPrompt)
                .manifest(row.manifest)
                .specExtra(row.specExtra)
                .parametersSchema(row.parametersSchema)
                .endpoint(row.endpoint)
                .protocol(row.protocol)
                .authType(row.authType)
                .authConfig(row.authConfig)
                .timeoutSec(row.timeoutSec)
                .probeConfig(row.probeConfig)
                .canaryPayload(row.canaryPayload)
                .build();
    }

    private void persistProbeOutcome(ResourceRow row, ResourceProbeResult result) {
        boolean healthy = "healthy".equalsIgnoreCase(result.healthStatus());
        long nextSuccess = healthy ? row.consecutiveSuccess + 1L : 0L;
        long nextFailure = healthy ? 0L : row.consecutiveFailure + 1L;
        String circuitState = normalizeCircuitState(row.currentState);
        String callabilityState = computeCallabilityState(row, result.healthStatus(), circuitState);
        String failureReason = result.failureReason();
        String callabilityReason = computeCallabilityReason(row, result.healthStatus(), circuitState, callabilityState, failureReason);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        UPDATE t_resource_runtime_policy
                        SET health_status = ?,
                            callability_state = ?,
                            callability_reason = ?,
                            last_probe_at = ?,
                            last_check_time = ?,
                            last_success_at = ?,
                            last_failure_at = ?,
                            last_failure_reason = ?,
                            consecutive_success = ?,
                            consecutive_failure = ?,
                            probe_latency_ms = ?,
                            probe_payload_summary = ?,
                            last_probe_evidence_json = ?,
                            update_time = NOW()
                        WHERE resource_id = ?
                        """,
                normalizeHealthStatus(result.healthStatus()),
                callabilityState,
                callabilityReason,
                now,
                now,
                healthy ? now : row.lastSuccessAt,
                healthy ? row.lastFailureAt : now,
                healthy ? null : failureReason,
                nextSuccess,
                nextFailure,
                result.latencyMs(),
                result.payloadSummary(),
                writeJson(result.evidence()),
                row.resourceId);
    }

    private ResourceRow loadResourceRow(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        Map<String, Object> base = queryOne("""
                SELECT id, resource_type, resource_code, display_name, status
                FROM t_resource
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, resourceId);
        if (base.isEmpty()) {
            return null;
        }
        String resourceType = normalizeType(base.get("resource_type"));
        Map<String, Object> policy = queryOne("""
                SELECT *
                FROM t_resource_runtime_policy
                WHERE resource_id = ?
                LIMIT 1
                """, resourceId);
        Map<String, Object> ext = loadExt(resourceId, resourceType);
        ResourceRow row = new ResourceRow();
        row.resourceId = resourceId;
        row.resourceType = resourceType;
        row.resourceCode = str(base.get("resource_code"));
        row.displayName = str(base.get("display_name"));
        row.resourceStatus = str(base.get("status"));
        row.policyId = longObject(policy.get("id"));
        row.healthStatus = str(policy.get("health_status"));
        row.currentState = str(policy.get("current_state"));
        row.lastProbeAt = toDateTime(policy.get("last_probe_at"));
        row.lastSuccessAt = toDateTime(policy.get("last_success_at"));
        row.lastFailureAt = toDateTime(policy.get("last_failure_at"));
        row.lastFailureReason = str(policy.get("last_failure_reason"));
        row.consecutiveSuccess = longValue(policy.get("consecutive_success"));
        row.consecutiveFailure = longValue(policy.get("consecutive_failure"));
        row.probeLatencyMs = longObject(policy.get("probe_latency_ms"));
        row.probePayloadSummary = str(policy.get("probe_payload_summary"));
        row.intervalSec = intObject(policy.get("interval_sec"), defaultInterval(resourceType));
        row.healthyThreshold = intObject(policy.get("healthy_threshold"), defaultHealthyThreshold(resourceType));
        row.timeoutSec = intObject(policy.get("timeout_sec"), defaultTimeout(resourceType));
        row.failureThreshold = intObject(policy.get("failure_threshold"), 5);
        row.openDurationSec = intObject(policy.get("open_duration_sec"), 60);
        row.halfOpenMaxCalls = intObject(policy.get("half_open_max_calls"), 3);
        row.checkType = firstText(str(policy.get("check_type")), defaultCheckType(resourceType, str(ext.get("protocol"))));
        row.checkUrl = firstText(str(policy.get("check_url")), defaultCheckUrl(resourceType, str(ext.get("upstream_endpoint")), str(ext.get("endpoint"))));
        row.probeStrategy = firstText(str(policy.get("probe_strategy")), defaultProbeStrategy(resourceType, str(ext.get("protocol"))));
        row.fallbackResourceCode = str(policy.get("fallback_resource_code"));
        row.fallbackMessage = str(policy.get("fallback_message"));
        row.probeConfig = parseMap(policy.get("probe_config_json"));
        row.canaryPayload = parseMap(policy.get("canary_payload_json"));
        row.lastProbeEvidence = parseMap(policy.get("last_probe_evidence_json"));
        if (TYPE_AGENT.equalsIgnoreCase(resourceType)) {
            row.resourceEnabled = boolValue(ext.get("enabled"));
            row.registrationProtocol = str(ext.get("registration_protocol"));
            row.upstreamEndpoint = str(ext.get("upstream_endpoint"));
            row.upstreamAgentId = str(ext.get("upstream_agent_id"));
            row.credentialRef = str(ext.get("credential_ref"));
            row.transformProfile = str(ext.get("transform_profile"));
            row.modelAlias = str(ext.get("model_alias"));
        } else if (TYPE_SKILL.equalsIgnoreCase(resourceType)) {
            row.resourceEnabled = true;
            row.executionMode = str(ext.get("execution_mode"));
            row.contextPrompt = str(ext.get("hosted_system_prompt"));
            row.manifest = parseMap(ext.get("manifest_json"));
            row.specExtra = parseMap(ext.get("spec_json"));
            row.parametersSchema = parseMap(ext.get("parameters_schema"));
        } else if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            row.resourceEnabled = true;
            row.endpoint = str(ext.get("endpoint"));
            row.protocol = str(ext.get("protocol"));
            row.authType = str(ext.get("auth_type"));
            row.authConfig = parseMap(ext.get("auth_config"));
        } else {
            row.resourceEnabled = true;
        }
        row.dependencies = dependencySnapshots(resourceType, resourceId);
        row.dependencyCount = row.dependencies.size();
        row.dependencyReason = dependencyReason(row.dependencies);
        return row;
    }

    private Map<String, Object> loadExt(Long resourceId, String resourceType) {
        if (!StringUtils.hasText(resourceType)) {
            return Map.of();
        }
        List<Map<String, Object>> rows = switch (resourceType.trim().toLowerCase(Locale.ROOT)) {
            case TYPE_AGENT -> jdbcTemplate.queryForList("""
                    SELECT enabled, registration_protocol, upstream_endpoint, upstream_agent_id,
                           credential_ref, transform_profile, model_alias
                    FROM t_resource_agent_ext
                    WHERE resource_id = ?
                    LIMIT 1
                    """, resourceId);
            case TYPE_SKILL -> jdbcTemplate.queryForList("""
                    SELECT execution_mode, hosted_system_prompt, manifest_json, spec_json, parameters_schema
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

    private List<ResourceHealthDependencyVO> dependencySnapshots(String resourceType, Long resourceId) {
        List<Long> ids = dependencyIds(resourceType, resourceId);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ResourceHealthDependencyVO> out = new ArrayList<>();
        for (Long id : ids) {
            ResourceHealthSnapshotVO dep = getSnapshot(id);
            if (dep == null) {
                out.add(ResourceHealthDependencyVO.builder()
                        .resourceId(id)
                        .resourceType(TYPE_MCP)
                        .displayName("Unknown MCP")
                        .callabilityState("not_found")
                        .callabilityReason("dependency snapshot missing")
                        .callable(false)
                        .build());
                continue;
            }
            out.add(ResourceHealthDependencyVO.builder()
                    .resourceId(dep.getResourceId())
                    .resourceType(dep.getResourceType())
                    .resourceCode(dep.getResourceCode())
                    .displayName(dep.getDisplayName())
                    .healthStatus(dep.getHealthStatus())
                    .callabilityState(dep.getCallabilityState())
                    .callabilityReason(dep.getCallabilityReason())
                    .callable(dep.getCallable())
                    .build());
        }
        return out;
    }

    private String dependencyReason(List<ResourceHealthDependencyVO> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return null;
        }
        List<String> blocked = new ArrayList<>();
        for (ResourceHealthDependencyVO dependency : dependencies) {
            if (!Boolean.TRUE.equals(dependency.getCallable())) {
                blocked.add(firstText(dependency.getCallabilityReason(), dependency.getResourceCode()));
            }
        }
        return blocked.isEmpty() ? null : String.join("; ", blocked);
    }

    private List<Long> dependencyIds(String resourceType, Long resourceId) {
        if (!StringUtils.hasText(resourceType) || resourceId == null) {
            return List.of();
        }
        String normalized = resourceType.trim().toLowerCase(Locale.ROOT);
        if (TYPE_AGENT.equals(normalized)) {
            return gatewayBindingExpansionService.listAgentBoundMcpIds(resourceId);
        }
        if (TYPE_SKILL.equals(normalized)) {
            return gatewayBindingExpansionService.listSkillBoundMcpIds(resourceId);
        }
        return List.of();
    }

    private String defaultProbeStrategy(String resourceType, String protocol) {
        if (TYPE_AGENT.equalsIgnoreCase(resourceType)) {
            return "agent_provider";
        }
        if (TYPE_SKILL.equalsIgnoreCase(resourceType)) {
            return "skill_canary";
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
            return "skill_bundle";
        }
        if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            return "stdio".equalsIgnoreCase(protocol) ? "mcp_stdio" : "mcp_jsonrpc";
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
        return 20;
    }

    private Map<String, Object> defaultProbeConfig(String resourceType, String protocol) {
        if (TYPE_AGENT.equalsIgnoreCase(resourceType)) {
            return Map.of("latencyThresholdMs", 1500);
        }
        if (TYPE_SKILL.equalsIgnoreCase(resourceType)) {
            return Map.of("mode", "canary");
        }
        if (TYPE_MCP.equalsIgnoreCase(resourceType)) {
            return "stdio".equalsIgnoreCase(protocol)
                    ? Map.of("requireTools", true, "transport", "sidecar")
                    : Map.of("requireTools", true);
        }
        return Map.of();
    }

    private Map<String, Object> defaultCanaryPayload(ResourceRow row) {
        if (row == null) {
            return Map.of();
        }
        if (TYPE_AGENT.equalsIgnoreCase(row.resourceType)) {
            return Map.of("query", "health check");
        }
        if (TYPE_SKILL.equalsIgnoreCase(row.resourceType)) {
            if (row.parametersSchema != null && !row.parametersSchema.isEmpty()) {
                Map<String, Object> generated = generatePayloadFromSchema(row.parametersSchema);
                if (!generated.isEmpty()) {
                    return generated;
                }
            }
            return Map.of("topic", row.displayName);
        }
        return Map.of();
    }

    private Map<String, Object> generatePayloadFromSchema(Map<String, Object> schema) {
        Object rawProperties = schema == null ? null : schema.get("properties");
        if (!(rawProperties instanceof Map<?, ?> properties)) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> definition)) {
                payload.put(key, "health-check");
                continue;
            }
            String type = firstText(str(definition.get("type")), "string");
            if ("number".equalsIgnoreCase(type) || "integer".equalsIgnoreCase(type)) {
                payload.put(key, 1);
            } else if ("boolean".equalsIgnoreCase(type)) {
                payload.put(key, Boolean.TRUE);
            } else if ("array".equalsIgnoreCase(type)) {
                payload.put(key, List.of("health-check"));
            } else if ("object".equalsIgnoreCase(type)) {
                payload.put(key, Map.of("probe", true));
            } else {
                payload.put(key, "health-check");
            }
        }
        return payload;
    }

    private String computeCallabilityState(ResourceRow row, String healthStatus, String circuitState) {
        if (row == null) {
            return "not_configured";
        }
        if (!"published".equalsIgnoreCase(firstText(row.resourceStatus, ""))) {
            return "not_published";
        }
        if (Boolean.FALSE.equals(row.resourceEnabled) || "disabled".equalsIgnoreCase(healthStatus)) {
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
        if (StringUtils.hasText(row.dependencyReason)) {
            return "dependency_blocked";
        }
        return CALLABLE;
    }

    private String computeCallabilityReason(ResourceRow row,
                                            String healthStatus,
                                            String circuitState,
                                            String callabilityState,
                                            String primaryReason) {
        return switch (callabilityState) {
            case CALLABLE -> StringUtils.hasText(primaryReason)
                    ? primaryReason
                    : ("degraded".equalsIgnoreCase(healthStatus) ? "resource callable with degraded health" : "resource callable");
            case "not_published" -> "resource is not published";
            case "disabled" -> "resource is disabled";
            case "circuit_open" -> "circuit breaker is open";
            case "circuit_half_open" -> "circuit breaker is half open";
            case "health_down" -> firstText(primaryReason, "health probe reported down");
            case "dependency_blocked" -> firstText(row == null ? null : row.dependencyReason, "dependency blocked");
            default -> "resource unavailable";
        };
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
                resourceType.trim().toLowerCase(Locale.ROOT),
                resourceId);
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
        payload.put("healthStatus", snapshot.getHealthStatus());
        payload.put("circuitState", snapshot.getCircuitState());
        payload.put("callabilityState", snapshot.getCallabilityState());
        payload.put("callabilityReason", snapshot.getCallabilityReason());
        payload.put("probeStrategy", snapshot.getProbeStrategy());
        log.debug("health snapshot changed: {}", payload);
        realtimePushService.getClass();
    }

    private Map<String, Object> queryOne(String sql, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, resourceId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
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

    private String writeJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizeType(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(value) ? value : null;
    }

    private static String normalizeHealthStatus(Object raw) {
        if (raw == null) {
            return "unknown";
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return StringUtils.hasText(value) ? value : "unknown";
    }

    private static String normalizeCircuitState(Object raw) {
        if (raw == null) {
            return "CLOSED";
        }
        String value = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        return StringUtils.hasText(value) ? value : "CLOSED";
    }

    private static String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static long longValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return raw == null ? 0L : Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static Long longObject(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return raw == null ? null : Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer intObject(Object raw, Integer fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return raw == null ? fallback : Integer.valueOf(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Boolean boolValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String value = String.valueOf(raw).trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static LocalDateTime toDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        try {
            return LocalDateTime.parse(String.valueOf(raw).replace(' ', 'T'));
        } catch (Exception ex) {
            return null;
        }
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
        Integer failureThreshold;
        Integer openDurationSec;
        Integer halfOpenMaxCalls;
        String checkType;
        String checkUrl;
        String probeStrategy;
        String fallbackResourceCode;
        String fallbackMessage;
        Map<String, Object> probeConfig;
        Map<String, Object> canaryPayload;
        Map<String, Object> lastProbeEvidence;
        Boolean resourceEnabled;
        String registrationProtocol;
        String upstreamEndpoint;
        String upstreamAgentId;
        String credentialRef;
        String transformProfile;
        String modelAlias;
        String executionMode;
        String contextPrompt;
        Map<String, Object> manifest;
        Map<String, Object> specExtra;
        Map<String, Object> parametersSchema;
        String endpoint;
        String protocol;
        String authType;
        Map<String, Object> authConfig;
        long dependencyCount;
        String dependencyReason;
        List<ResourceHealthDependencyVO> dependencies = List.of();
    }
}
