package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PerformanceAnalysisVO {

    private String window;
    private String resourceType;
    private Long resourceId;
    private PerformanceSummaryVO summary;
    private List<PerformanceBucketVO> buckets;
    private List<PerformanceResourceLeaderboardVO> resourceLeaderboard;
    private List<PerformanceSlowMethodVO> slowMethods;
    private String compareWindow;
    private PerformanceSummaryVO compareSummary;
    private List<PerformanceSlowMethodVO> methodLeaderboard;
}
