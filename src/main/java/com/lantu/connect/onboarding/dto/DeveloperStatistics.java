package com.lantu.connect.onboarding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeveloperStatistics {

    private Long totalCalls;
    private Long todayCalls;
    private Double errorRate;
    private Double avgLatencyMs;

    private List<Map<String, Object>> callsByDay;
    private List<Map<String, Object>> topResources;
    private List<Map<String, Object>> apiKeyUsage;
}
