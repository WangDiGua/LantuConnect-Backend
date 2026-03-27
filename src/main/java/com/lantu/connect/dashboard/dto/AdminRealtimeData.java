package com.lantu.connect.dashboard.dto;

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
public class AdminRealtimeData {

    private Long todayCalls;
    private Long todayErrors;
    private Double avgLatencyMs;
    private Long activeUsers;

    private List<Map<String, Object>> callTrend;
    private List<Map<String, Object>> resourceTrend;
    private List<Map<String, Object>> userGrowth;

    private Long pendingAudits;
    private Long activeAlerts;

    private List<Map<String, Object>> topResourcesByCall;
    private List<Map<String, Object>> systemHealth;
}
