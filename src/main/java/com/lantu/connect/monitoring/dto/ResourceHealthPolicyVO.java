package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ResourceHealthPolicyVO {

    private String checkType;
    private String checkUrl;
    private String probeStrategy;
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
