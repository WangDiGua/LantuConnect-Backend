package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerformanceSlowMethodVO {

    private String method;
    private long requestCount;
    private long errorCount;
    private double errorRate;
    private double avgLatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
}
