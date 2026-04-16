package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 资源健康快照。
 *
 * 统一承载 agent / skill / mcp 的健康状态、熔断状态与可调用原因，供管理端与网关共用。
 */
@Data
@Builder
public class ResourceHealthSnapshotVO {

    private Long resourceId;
    private String resourceType;
    private String resourceCode;
    private String displayName;
    private String resourceStatus;
    private String probeStrategy;
    private String checkType;
    private String checkUrl;
    private String healthStatus;
    private String circuitState;
    private String callabilityState;
    private String callabilityReason;
    private Boolean callable;
    private Boolean resourceEnabled;
    private LocalDateTime lastProbeAt;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;
    private String lastFailureReason;
    private Long consecutiveSuccess;
    private Long consecutiveFailure;
    private Long probeLatencyMs;
    private String probePayloadSummary;
    private Integer intervalSec;
    private Integer healthyThreshold;
    private Integer timeoutSec;
    private Map<String, Object> probeEvidence;
    private Map<String, Object> lastProbeEvidence;
    private ResourceHealthPolicyVO policy;
    private List<ResourceHealthDependencyVO> dependencies;
}
