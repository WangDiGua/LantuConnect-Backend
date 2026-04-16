package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceHealthDependencyVO {

    private Long resourceId;
    private String resourceType;
    private String resourceCode;
    private String displayName;
    private String healthStatus;
    private String callabilityState;
    private String callabilityReason;
    private Boolean callable;
}
