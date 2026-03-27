package com.lantu.connect.gateway.dto;

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
public class ResourceStatsVO {

    private Long callCount;
    private Double successRate;
    private Double rating;
    private Long favoriteCount;
    private List<Map<String, Object>> callTrend;
    private List<Map<String, Object>> relatedResources;
}
