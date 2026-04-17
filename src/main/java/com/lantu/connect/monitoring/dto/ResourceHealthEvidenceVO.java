package com.lantu.connect.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceHealthEvidenceVO {

    private Long resourceId;
    private String resourceType;
    private String resourceCode;
    private String displayName;
    private String healthStatus;
    private String circuitState;
    private String callabilityState;
    private String callabilityReason;
    private String lastFailureReason;
    private LocalDateTime lastFailureAt;
}
