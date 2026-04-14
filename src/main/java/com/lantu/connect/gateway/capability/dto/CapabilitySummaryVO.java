package com.lantu.connect.gateway.capability.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CapabilitySummaryVO {

    private Long capabilityId;

    private String capabilityType;

    private String displayName;

    private String resourceCode;

    private String description;

    private String status;

    private String runtimeMode;

    private String invokeMode;

    private Long callCount;

    private Long viewCount;

    private Double ratingAvg;

    private Long reviewCount;

    private List<String> tags;
}
