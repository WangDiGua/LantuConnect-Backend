package com.lantu.connect.monitoring.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ResourceHealthPolicyUpdateRequest {

    private Integer intervalSec;
    private Integer healthyThreshold;
    private Integer timeoutSec;
    private Integer failureThreshold;
    private Integer openDurationSec;
    private Integer halfOpenMaxCalls;
    private String fallbackResourceCode;
    private String fallbackMessage;
    private Map<String, Object> probeConfig;
    private Map<String, Object> canaryPayload;
}
