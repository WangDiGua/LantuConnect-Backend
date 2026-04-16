package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerformanceResourceLeaderboardVO {

    private String resourceType;
    private Long resourceId;
    private String resourceName;
    private long requestCount;
    private long errorCount;
    private long timeoutCount;
    private double errorRate;
    private double timeoutRate;
    private double avgLatencyMs;
    private double p99LatencyMs;
    private boolean lowSample;
}
