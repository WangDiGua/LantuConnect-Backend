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
public class UserDashboardData {

    private Map<String, Object> quotaUsage;
    private Map<String, Object> myResources;
    private List<Map<String, Object>> recentActivity;
    private Long unreadNotifications;
}
