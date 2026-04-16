package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerformanceBucketVO {

    private String bucket;
    private long requestCount;
    private long successCount;
    private long errorCount;
    private long timeoutCount;
    private double successRate;
    private double errorRate;
    private double timeoutRate;
    private double avgLatencyMs;
    private double p50LatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
    private double throughput;
}
