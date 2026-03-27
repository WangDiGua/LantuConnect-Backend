package com.lantu.connect.dashboard.service;

import com.lantu.connect.dashboard.dto.AdminOverviewVO;
import com.lantu.connect.dashboard.dto.AdminRealtimeData;
import com.lantu.connect.dashboard.dto.ExploreHubData;
import com.lantu.connect.dashboard.dto.UsageStatsVO;
import com.lantu.connect.dashboard.dto.UserDashboardData;
import com.lantu.connect.dashboard.dto.UserWorkspaceVO;

import java.util.Map;

/**
 * 仪表盘Dashboard服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface DashboardService {

    AdminOverviewVO adminOverview(Long operatorUserId);

    UserWorkspaceVO userWorkspace(Long userId);

    Map<String, Object> healthSummary();

    UsageStatsVO usageStats(String range);

    Map<String, Object> dataReports(String range);

    Map<String, Object> dataReports(String range, String startDate, String endDate);

    ExploreHubData exploreHub(Long userId);

    AdminRealtimeData adminRealtime();

    UserDashboardData userDashboard(Long userId);
}
