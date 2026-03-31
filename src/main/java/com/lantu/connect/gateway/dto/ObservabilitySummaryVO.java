package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ObservabilitySummaryVO {

    private Long resourceId;
    private String resourceType;
    private String resourceCode;
    private String displayName;
    private String healthStatus;
    private String circuitState;
    private Integer qualityScore;
    private Map<String, Object> qualityFactors;
    private DegradationHintVO degradationHint;
    private LocalDateTime generatedAt;
}
