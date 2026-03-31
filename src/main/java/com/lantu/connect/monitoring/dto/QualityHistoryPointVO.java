package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QualityHistoryPointVO {
    private LocalDateTime bucketTime;
    private Long callCount;
    private Double successRate;
    private Double avgLatencyMs;
    private Integer qualityScore;
}
