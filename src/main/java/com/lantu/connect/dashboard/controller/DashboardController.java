package com.lantu.connect.dashboard.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.dashboard.dto.AdminOverviewVO;
import com.lantu.connect.dashboard.dto.AdminRealtimeData;
import com.lantu.connect.dashboard.dto.ExploreHubData;
import com.lantu.connect.dashboard.dto.OwnerDeveloperStatsVO;
import com.lantu.connect.dashboard.dto.UsageStatsVO;
import com.lantu.connect.dashboard.dto.UserDashboardData;
import com.lantu.connect.dashboard.dto.UserWorkspaceVO;
import com.lantu.connect.dashboard.service.DashboardService;
import com.lantu.connect.dashboard.service.OwnerDeveloperStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final OwnerDeveloperStatsService ownerDeveloperStatsService;

    /**
     * 管理总览（全平台口径；需 monitor:view，通常 reviewer / 超管）。
     * <p>{@code summary} 键含义：<ul>
     * <li>{@code totalUsers} — {@code t_user} 未删除用户总数</li>
     * <li>{@code totalAgents}/{@code totalSkills}/{@code totalApps}/{@code totalDatasets} — {@code t_resource} 按 {@code resource_type} 且 {@code deleted=0}</li>
     * <li>{@code callLogsToday} — 当日 {@code t_call_log} 条数（网关侧调用日志，非 usage_record 全量）</li>
     * <li>{@code pendingAudits} — {@code t_audit_item.status=pending_review} 数量（全平台待审）</li>
     * </ul>
     * {@code charts[0].id=calls_last_7d}：近 7 日按天的 call_log 计数，与「用户用量」口径不同。
     */
    @GetMapping("/admin-overview")
    @RequirePermission({"monitor:view"})
    public R<AdminOverviewVO> adminOverview(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(dashboardService.adminOverview(userId));
    }

    @GetMapping("/user-workspace")
    public R<UserWorkspaceVO> userWorkspace(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(dashboardService.userWorkspace(userId));
    }

    /**
     * Owner 维度统计（网关 invoke / usage_record invoke / 技能包下载）。权限：本人、reviewer、platform_admin/admin。
     */
    @GetMapping("/owner-resource-stats")
    public R<OwnerDeveloperStatsVO> ownerResourceStats(
            @RequestHeader("X-User-Id") Long operatorUserId,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false, defaultValue = "7") Integer periodDays) {
        int days = periodDays == null ? 7 : periodDays;
        return R.ok(ownerDeveloperStatsService.ownerResourceStats(operatorUserId, ownerUserId, days));
    }

    @GetMapping("/health-summary")
    @RequirePermission({"monitor:view"})
    public R<Map<String, Object>> healthSummary() {
        return R.ok(dashboardService.healthSummary());
    }

    @GetMapping("/usage-stats")
    @RequirePermission({"monitor:view"})
    public R<UsageStatsVO> usageStats(@RequestParam(required = false) String range) {
        return R.ok(dashboardService.usageStats(range));
    }

    @GetMapping("/data-reports")
    @RequirePermission({"monitor:view"})
    public R<Map<String, Object>> dataReports(@RequestParam(required = false) String range,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        if (startDate != null && endDate != null) {
            return R.ok(dashboardService.dataReports(range, startDate, endDate));
        }
        return R.ok(dashboardService.dataReports(range));
    }

    @GetMapping("/explore-hub")
    public R<ExploreHubData> exploreHub(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(dashboardService.exploreHub(userId));
    }

    @GetMapping("/admin-realtime")
    @RequirePermission({"monitor:view"})
    public R<AdminRealtimeData> adminRealtime() {
        return R.ok(dashboardService.adminRealtime());
    }

    @GetMapping("/user-dashboard")
    public R<UserDashboardData> userDashboard(@RequestHeader("X-User-Id") String userIdHeader) {
        Long userId = parseOptionalUserId(userIdHeader);
        if (userId == null) {
            return R.fail(ResultCode.PARAM_ERROR, "无效的 X-User-Id");
        }
        return R.ok(dashboardService.userDashboard(userId));
    }

    /**
     * 解析可选用户头：空、undefined、null、非数字均视为未登录上下文（返回 null）。
     */
    private static Long parseOptionalUserId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if ("undefined".equalsIgnoreCase(t) || "null".equalsIgnoreCase(t)) {
            return null;
        }
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
