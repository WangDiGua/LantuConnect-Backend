package com.lantu.connect.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.audit.mapper.AuditItemMapper;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.dashboard.dto.AdminOverviewVO;
import com.lantu.connect.dashboard.dto.AdminRealtimeData;
import com.lantu.connect.dashboard.dto.ExploreHubData;
import com.lantu.connect.dashboard.dto.UsageStatsVO;
import com.lantu.connect.dashboard.dto.UserDashboardData;
import com.lantu.connect.dashboard.dto.UserWorkspaceVO;
import com.lantu.connect.dashboard.service.DashboardService;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.CircuitBreaker;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.CircuitBreakerMapper;
import com.lantu.connect.monitoring.mapper.HealthConfigMapper;
import com.lantu.connect.notification.entity.Notification;
import com.lantu.connect.notification.mapper.NotificationMapper;
import com.lantu.connect.sysconfig.entity.Announcement;
import com.lantu.connect.sysconfig.mapper.AnnouncementMapper;
import com.lantu.connect.useractivity.entity.Favorite;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.lantu.connect.useractivity.mapper.FavoriteMapper;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘Dashboard服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final CallLogMapper callLogMapper;
    private final AuditItemMapper auditItemMapper;
    private final UsageRecordMapper usageRecordMapper;
    private final FavoriteMapper favoriteMapper;
    private final NotificationMapper notificationMapper;
    private final HealthConfigMapper healthConfigMapper;
    private final CircuitBreakerMapper circuitBreakerMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final AnnouncementMapper announcementMapper;

    /**
     * 管理员总览：聚合用户、资源、审核与调用趋势等全局指标。
     */
    @Override
    public AdminOverviewVO adminOverview(Long operatorUserId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalUsers", userMapper.selectCount(null));
        summary.put("totalAgents", countResourceByType("agent"));
        summary.put("totalSkills", countResourceByType("skill"));
        summary.put("totalApps", countResourceByType("app"));
        summary.put("totalDatasets", countResourceByType("dataset"));
        Long todayCalls = callLogMapper.selectTodayCount();
        summary.put("callLogsToday", todayCalls != null ? todayCalls : 0L);
        Long pending = auditItemMapper.selectCount(
                new LambdaQueryWrapper<AuditItem>().eq(AuditItem::getStatus, "pending"));
        summary.put("pendingAudits", pending != null ? pending : 0L);

        List<Map<String, Object>> charts = new ArrayList<>();
        QueryWrapper<CallLog> q = new QueryWrapper<>();
        q.select("DATE(create_time) AS day", "COUNT(*) AS cnt")
                .ge("create_time", LocalDateTime.now().minusDays(7))
                .groupBy("DATE(create_time)")
                .orderByAsc("DATE(create_time)");
        List<Map<String, Object>> callTrend = callLogMapper.selectMaps(q);
        charts.add(Map.of(
                "id", "calls_last_7d",
                "title", "近7日调用量",
                "data", callTrend != null ? callTrend : List.of()));

        Map<String, Object> extras = new HashMap<>();
        extras.put("generatedAt", LocalDateTime.now());

        return AdminOverviewVO.builder()
                .summary(summary)
                .charts(charts)
                .extras(extras)
                .build();
    }

    /**
     * 用户工作台：返回个人档案、最近行为与提醒小组件。
     */
    @Override
    public UserWorkspaceVO userWorkspace(Long userId) {
        User user = userMapper.selectById(userId);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", userId);
        if (user != null) {
            profile.put("username", user.getUsername());
            profile.put("realName", user.getRealName());
            profile.put("status", user.getStatus());
            profile.put("mail", user.getMail());
        }

        List<UsageRecord> recentRows = usageRecordMapper.selectList(
                new LambdaQueryWrapper<UsageRecord>()
                        .eq(UsageRecord::getUserId, userId)
                        .orderByDesc(UsageRecord::getCreateTime)
                        .last("LIMIT 8"));
        List<Map<String, Object>> recent = new ArrayList<>();
        for (UsageRecord r : recentRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("action", r.getAction());
            row.put("targetType", r.getType());
            row.put("targetId", r.getAgentName());
            row.put("resourceId", r.getResourceId());
            row.put("displayName", r.getDisplayName() != null ? r.getDisplayName() : r.getAgentName());
            row.put("createTime", r.getCreateTime());
            recent.add(row);
        }

        Long favCount = favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId));
        Long unread = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false));

        Map<String, Object> widgets = new LinkedHashMap<>();
        widgets.put("favoriteCount", favCount != null ? favCount : 0L);
        widgets.put("unreadNotifications", unread != null ? unread : 0L);

        return UserWorkspaceVO.builder()
                .profile(profile)
                .recent(recent)
                .widgets(widgets)
                .build();
    }

    /**
     * 健康摘要：展示治理配置规模与当前开断状态概览。
     */
    @Override
    public Map<String, Object> healthSummary() {
        long cfg = healthConfigMapper.selectCount(null);
        long open = circuitBreakerMapper.selectCount(
                new LambdaQueryWrapper<CircuitBreaker>().eq(CircuitBreaker::getCurrentState, CircuitBreaker.STATE_OPEN));
        List<Map<String, Object>> checks = jdbcTemplate.queryForList(
                "SELECT resource_id, resource_type, resource_code, display_name, health_status, last_check_time "
                        + "FROM t_resource_health_config ORDER BY update_time DESC LIMIT 20");
        List<Map<String, Object>> degraded = jdbcTemplate.queryForList(
                "SELECT h.resource_id, h.resource_type, h.resource_code, h.display_name, h.health_status, "
                        + "COALESCE(cb.current_state, 'unknown') AS circuit_state "
                        + "FROM t_resource_health_config h "
                        + "LEFT JOIN t_resource_circuit_breaker cb ON cb.resource_id = h.resource_id "
                        + "WHERE h.health_status <> 'healthy' OR cb.current_state = 'OPEN' "
                        + "ORDER BY h.update_time DESC LIMIT 20");
        List<Map<String, Object>> statusDistRows = jdbcTemplate.queryForList(
                "SELECT health_status, COUNT(1) AS cnt FROM t_resource_health_config GROUP BY health_status");
        Map<String, Long> statusDistribution = new LinkedHashMap<>();
        for (Map<String, Object> row : statusDistRows) {
            statusDistribution.put(String.valueOf(row.get("health_status")),
                    row.get("cnt") instanceof Number n ? n.longValue() : 0L);
        }
        String status = open > 0 || !degraded.isEmpty() ? "degraded" : "ok";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("healthConfigCount", cfg);
        out.put("circuitBreakersOpen", open);
        out.put("checks", checks == null ? List.of() : checks);
        out.put("statusDistribution", statusDistribution);
        out.put("degradedResources", degraded == null ? List.of() : degraded);
        return out;
    }

    @Override
    public UsageStatsVO usageStats(String range) {
        String r = StringUtils.hasText(range) ? range : "7d";
        ReportWindow w = resolveReportWindow(r, null, null);
        QueryWrapper<CallLog> q = new QueryWrapper<>();
        q.select("DATE(create_time) AS day", "COUNT(*) AS cnt", "ROUND(AVG(latency_ms), 2) AS avgLatencyMs")
                .ge("create_time", w.from())
                .le("create_time", w.toInclusive())
                .groupBy("DATE(create_time)")
                .orderByAsc("DATE(create_time)");
        List<Map<String, Object>> series = callLogMapper.selectMaps(q);

        Map<String, Object> aggregates = new LinkedHashMap<>();
        aggregates.put("range", r);
        Long totalToday = callLogMapper.selectTodayCount();
        aggregates.put("callLogsToday", totalToday != null ? totalToday : 0L);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        QueryWrapper<CallLog> topPath = new QueryWrapper<>();
        topPath.select("method AS path", "COUNT(*) AS cnt")
                .ge("create_time", w.from())
                .le("create_time", w.toInclusive())
                .groupBy("method")
                .orderByDesc("cnt")
                .last("LIMIT 10");
        breakdown.put("topPaths", callLogMapper.selectMaps(topPath));
        breakdown.put("callsByResourceType", buildCallsByResourceTypeRows(w.from(), w.toInclusive()));

        return UsageStatsVO.builder()
                .aggregates(aggregates)
                .series(series != null ? series : List.of())
                .breakdown(breakdown)
                .build();
    }

    @Override
    public Map<String, Object> dataReports(String range) {
        ReportWindow w = resolveReportWindow(StringUtils.hasText(range) ? range : "7d", null, null);
        return buildDataReportsBody(w, StringUtils.hasText(range) ? range : "7d", null, null);
    }

    @Override
    public Map<String, Object> dataReports(String range, String startDate, String endDate) {
        ReportWindow w = resolveReportWindow(
                StringUtils.hasText(range) ? range : "7d", startDate, endDate);
        String rangeLabel = StringUtils.hasText(startDate) && StringUtils.hasText(endDate) ? "custom"
                : (StringUtils.hasText(range) ? range : "custom");
        return buildDataReportsBody(w, rangeLabel, startDate, endDate);
    }

    /** 数据报表 / 用量统计共用的时间窗口。 */
    private record ReportWindow(LocalDateTime from, LocalDateTime toInclusive) {}

    private ReportWindow resolveReportWindow(String range, String startDate, String endDate) {
        if (StringUtils.hasText(startDate) && StringUtils.hasText(endDate)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDateTime from = LocalDate.parse(startDate, fmt).atStartOfDay();
            LocalDateTime to = LocalDate.parse(endDate, fmt).atTime(23, 59, 59);
            return new ReportWindow(from, to);
        }
        LocalDateTime to = LocalDateTime.now();
        String r = StringUtils.hasText(range) ? range.trim() : "7d";
        if ("today".equalsIgnoreCase(r)) {
            return new ReportWindow(LocalDate.now().atStartOfDay(), to);
        }
        int days = r.endsWith("d") ? parseDays(r) : 7;
        return new ReportWindow(to.minusDays(days), to);
    }

    private Map<String, Object> buildDataReportsBody(ReportWindow w, String rangeLabel, String startDate, String endDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("range", rangeLabel);
        if (startDate != null) {
            body.put("startDate", startDate);
        }
        if (endDate != null) {
            body.put("endDate", endDate);
        }
        List<Map<String, Object>> methodRows = jdbcTemplate.queryForList(
                "SELECT method AS path, COUNT(*) AS requests, ROUND(AVG(latency_ms), 2) AS avgLatencyMs "
                        + "FROM t_call_log WHERE create_time >= ? AND create_time <= ? "
                        + "GROUP BY method ORDER BY requests DESC LIMIT 20",
                w.from(), w.toInclusive());
        body.put("rows", methodRows != null ? methodRows : List.of());
        body.put("callsByResourceType", buildCallsByResourceTypeRows(w.from(), w.toInclusive()));

        List<Map<String, Object>> topRaw = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') AS resource_type, agent_name, "
                        + "COUNT(*) AS calls, SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_calls "
                        + "FROM t_call_log WHERE create_time >= ? AND create_time <= ? "
                        + "GROUP BY COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown'), agent_name "
                        + "ORDER BY calls DESC LIMIT 40",
                w.from(), w.toInclusive());
        List<Map<String, Object>> topResources = new ArrayList<>();
        for (Map<String, Object> row : topRaw) {
            topResources.add(mapTopResourceRow(row));
        }
        body.put("topResources", topResources);
        body.put("topAgents", filterTopByResourceType(topResources, "agent", 10));
        body.put("topSkills", filterTopByResourceType(topResources, "skill", 10));
        body.put("topMcps", filterTopByResourceType(topResources, "mcp", 10));
        body.put("topApps", filterTopByResourceType(topResources, "app", 10));
        body.put("topDatasets", filterTopByResourceType(topResources, "dataset", 10));

        List<Map<String, Object>> deptRaw = jdbcTemplate.queryForList(
                "SELECT COALESCE(u.school_id, -1) AS department_key, "
                        + "COUNT(DISTINCT cl.user_id) AS users, COUNT(*) AS calls "
                        + "FROM t_call_log cl "
                        + "LEFT JOIN t_user u ON u.user_id = CAST(cl.user_id AS UNSIGNED) AND u.deleted = 0 "
                        + "WHERE cl.create_time >= ? AND cl.create_time <= ? "
                        + "GROUP BY COALESCE(u.school_id, -1) ORDER BY calls DESC LIMIT 25",
                w.from(), w.toInclusive());
        List<Map<String, Object>> departmentUsage = new ArrayList<>();
        for (Map<String, Object> row : deptRaw) {
            Map<String, Object> m = new LinkedHashMap<>();
            Object key = row.get("department_key");
            long k = key instanceof Number n ? n.longValue() : -1L;
            m.put("department", k < 0 ? "未关联用户/院系" : ("院系 ID " + k));
            m.put("users", numberToLong(row.get("users")));
            m.put("calls", numberToLong(row.get("calls")));
            departmentUsage.add(m);
        }
        body.put("departmentUsage", departmentUsage);
        return body;
    }

    private List<Map<String, Object>> buildCallsByResourceTypeRows(LocalDateTime from, LocalDateTime toInclusive) {
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') AS resource_type, "
                        + "COUNT(*) AS calls, SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_calls "
                        + "FROM t_call_log WHERE create_time >= ? AND create_time <= ? "
                        + "GROUP BY COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') ORDER BY calls DESC",
                from, toInclusive);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            out.add(mapTypeCallsRow(row));
        }
        return out;
    }

    private static Map<String, Object> mapTypeCallsRow(Map<String, Object> row) {
        long calls = numberToLong(row.get("calls"));
        long success = numberToLong(row.get("success_calls"));
        double rate = calls > 0 ? Math.round(success * 1000.0 / calls) / 10.0 : 0.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", String.valueOf(row.get("resource_type")));
        m.put("calls", calls);
        m.put("successRate", rate);
        return m;
    }

    private static Map<String, Object> mapTopResourceRow(Map<String, Object> row) {
        long calls = numberToLong(row.get("calls"));
        long success = numberToLong(row.get("success_calls"));
        double rate = calls > 0 ? Math.round(success * 1000.0 / calls) / 10.0 : 0.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resourceType", String.valueOf(row.get("resource_type")));
        m.put("name", String.valueOf(row.get("agent_name")));
        m.put("calls", calls);
        m.put("successRate", rate);
        return m;
    }

    private static List<Map<String, Object>> filterTopByResourceType(List<Map<String, Object>> all, String type, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : all) {
            if (type.equalsIgnoreCase(String.valueOf(r.get("resourceType")))) {
                out.add(r);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public ExploreHubData exploreHub(Long userId) {
        Map<String, Object> platformStats = new LinkedHashMap<>();
        long totalAgents = countPublishedResourceByType("agent");
        long totalSkills = countPublishedResourceByType("skill");
        long totalMcps = countPublishedResourceByType("mcp");
        long totalApps = countPublishedResourceByType("app");
        long totalDatasets = countPublishedResourceByType("dataset");
        platformStats.put("totalAgents", totalAgents);
        platformStats.put("totalSkills", totalSkills);
        platformStats.put("totalMcps", totalMcps);
        platformStats.put("totalApps", totalApps);
        platformStats.put("totalDatasets", totalDatasets);
        platformStats.put("totalResources", totalAgents + totalSkills + totalMcps + totalApps + totalDatasets);
        platformStats.put("totalUsers", userMapper.selectCount(null));
        Long todayCalls = callLogMapper.selectTodayCount();
        platformStats.put("totalCallsToday", todayCalls != null ? todayCalls : 0L);
        platformStats.put("callsTrend7d", buildDailyTrend(
                "SELECT DATE(create_time) AS day, COUNT(*) AS cnt "
                        + "FROM t_call_log WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
                        + "GROUP BY DATE(create_time)",
                "calls"));
        platformStats.put("newResourcesTrend7d", buildDailyTrend(
                "SELECT DATE(create_time) AS day, COUNT(*) AS cnt "
                        + "FROM t_resource WHERE deleted = 0 AND status = 'published' "
                        + "AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
                        + "GROUP BY DATE(create_time)",
                "count"));
        platformStats.put("byType", jdbcTemplate.queryForList(
                "SELECT resource_type AS type, COUNT(*) AS cnt FROM t_resource WHERE deleted = 0 AND status = 'published' "
                        + "GROUP BY resource_type"));

        List<ExploreHubData.ExploreResourceItem> trending = jdbcTemplate.query(
                "SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status, "
                        + "COALESCE(NULLIF(TRIM(u.real_name), ''), u.username) AS author, "
                        + "COALESCE(c.cnt, 0) AS call_count, "
                        + "COALESCE(f.fav_cnt, 0) AS favorite_count, "
                        + "COALESCE(rv.review_count, 0) AS review_count, "
                        + "COALESCE(rv.avg_rating, 0) AS rating, "
                        + "r.update_time "
                        + "FROM t_resource r "
                        + "LEFT JOIN t_user u ON r.created_by = u.user_id "
                        + "LEFT JOIN (SELECT agent_id, COUNT(*) AS cnt FROM t_call_log "
                        + "WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) GROUP BY agent_id) c "
                        + "ON r.id = c.agent_id "
                        + "LEFT JOIN (SELECT target_type, target_id, COUNT(*) AS fav_cnt FROM t_favorite "
                        + "GROUP BY target_type, target_id) f ON r.id = f.target_id AND r.resource_type = f.target_type "
                        + "LEFT JOIN (SELECT target_type, target_id, AVG(rating) AS avg_rating, COUNT(*) AS review_count FROM t_review "
                        + "WHERE deleted = 0 GROUP BY target_type, target_id) rv ON r.id = rv.target_id AND r.resource_type = rv.target_type "
                        + "WHERE r.deleted = 0 AND r.status = 'published' "
                        + "ORDER BY call_count DESC LIMIT 10",
                (rs, i) -> ExploreHubData.ExploreResourceItem.builder()
                        .resourceId(String.valueOf(rs.getLong("id")))
                        .resourceType(rs.getString("resource_type"))
                        .resourceCode(rs.getString("resource_code"))
                        .displayName(rs.getString("display_name"))
                        .description(rs.getString("description"))
                        .status(rs.getString("status"))
                        .callCount(rs.getLong("call_count"))
                        .favoriteCount(rs.getLong("favorite_count"))
                        .reviewCount(rs.getLong("review_count"))
                        .rating(rs.getDouble("rating"))
                        .author(rs.getString("author"))
                        .publishedAt(rs.getTimestamp("update_time") != null ? rs.getTimestamp("update_time").toLocalDateTime() : null)
                        .build());

        List<ExploreHubData.ExploreResourceItem> recentPublished = jdbcTemplate.query(
                "SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status, r.update_time, "
                        + "COALESCE(NULLIF(TRIM(u.real_name), ''), u.username) AS author, "
                        + "COALESCE(cl_all.cnt, 0) AS call_count, "
                        + "COALESCE(f.fav_cnt, 0) AS favorite_count, COALESCE(rv.review_count, 0) AS review_count, COALESCE(rv.avg_rating, 0) AS rating "
                        + "FROM t_resource r "
                        + "LEFT JOIN t_user u ON r.created_by = u.user_id "
                        + "LEFT JOIN (SELECT agent_id, COUNT(*) AS cnt FROM t_call_log GROUP BY agent_id) cl_all "
                        + "ON r.id = cl_all.agent_id "
                        + "LEFT JOIN (SELECT target_type, target_id, COUNT(*) AS fav_cnt FROM t_favorite GROUP BY target_type, target_id) f "
                        + "ON r.id = f.target_id AND r.resource_type = f.target_type "
                        + "LEFT JOIN (SELECT target_type, target_id, AVG(rating) AS avg_rating, COUNT(*) AS review_count FROM t_review "
                        + "WHERE deleted = 0 GROUP BY target_type, target_id) rv ON r.id = rv.target_id AND r.resource_type = rv.target_type "
                        + "WHERE r.deleted = 0 AND r.status = 'published' "
                        + "ORDER BY update_time DESC LIMIT 10",
                (rs, i) -> ExploreHubData.ExploreResourceItem.builder()
                        .resourceId(String.valueOf(rs.getLong("id")))
                        .resourceType(rs.getString("resource_type"))
                        .resourceCode(rs.getString("resource_code"))
                        .displayName(rs.getString("display_name"))
                        .description(rs.getString("description"))
                        .status(rs.getString("status"))
                        .callCount(rs.getLong("call_count"))
                        .favoriteCount(rs.getLong("favorite_count"))
                        .reviewCount(rs.getLong("review_count"))
                        .rating(rs.getDouble("rating"))
                        .author(rs.getString("author"))
                        .publishedAt(rs.getTimestamp("update_time") != null ? rs.getTimestamp("update_time").toLocalDateTime() : null)
                        .build());

        String preferredTypeByRecentUse = findPreferredTypeByRecentUse(userId);
        String preferredTypeByFavorite = findPreferredTypeByFavorite(userId);
        List<ExploreHubData.ExploreResourceItem> recommended = recentPublished.stream()
                .map(item -> ExploreHubData.ExploreResourceItem.builder()
                        .resourceId(item.getResourceId())
                        .resourceType(item.getResourceType())
                        .resourceCode(item.getResourceCode())
                        .displayName(item.getDisplayName())
                        .description(item.getDescription())
                        .status(item.getStatus())
                        .callCount(item.getCallCount())
                        .favoriteCount(item.getFavoriteCount())
                        .reviewCount(item.getReviewCount())
                        .rating(item.getRating())
                        .author(item.getAuthor())
                        .reason(buildRecommendationReason(item.getResourceType(), preferredTypeByRecentUse, preferredTypeByFavorite))
                        .publishedAt(item.getPublishedAt())
                        .build())
                .limit(6)
                .toList();

        List<ExploreHubData.AnnouncementItem> announcements = announcementMapper.selectList(
                        new LambdaQueryWrapper<Announcement>()
                                .orderByDesc(Announcement::getPinned)
                                .orderByDesc(Announcement::getCreateTime)
                                .last("LIMIT 5"))
                .stream()
                .map(a -> ExploreHubData.AnnouncementItem.builder()
                        .id(a.getId())
                        .title(a.getTitle())
                        .summary(a.getSummary())
                        .type(a.getType())
                        .pinned(a.getPinned())
                        .createdAt(a.getCreateTime())
                        .createTime(a.getCreateTime())
                        .content(a.getContent())
                        .build())
                .toList();

        List<ExploreHubData.ContributorItem> topContributors = jdbcTemplate.query(
                "SELECT r.created_by, u.real_name, u.head_image, "
                        + "COUNT(*) AS resource_count, "
                        + "COALESCE(SUM(cl.total_calls), 0) AS total_calls, "
                        + "SUM(CASE WHEN r.create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS weekly_new_resources, "
                        + "COALESCE(SUM(cl7.total_calls_7d), 0) AS weekly_calls, "
                        + "COALESCE(SUM(fav.total_favorites), 0) AS like_count "
                        + "FROM t_resource r "
                        + "LEFT JOIN t_user u ON r.created_by = u.user_id "
                        + "LEFT JOIN (SELECT agent_id, COUNT(*) AS total_calls FROM t_call_log GROUP BY agent_id) cl "
                        + "ON r.id = cl.agent_id "
                        + "LEFT JOIN (SELECT agent_id, COUNT(*) AS total_calls_7d FROM t_call_log "
                        + "WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) GROUP BY agent_id) cl7 ON r.id = cl7.agent_id "
                        + "LEFT JOIN (SELECT target_type, target_id, COUNT(*) AS total_favorites FROM t_favorite "
                        + "GROUP BY target_type, target_id) fav ON r.id = fav.target_id AND r.resource_type = fav.target_type "
                        + "WHERE r.deleted = 0 AND r.status = 'published' AND r.created_by IS NOT NULL "
                        + "GROUP BY r.created_by, u.real_name, u.head_image "
                        + "ORDER BY weekly_new_resources DESC, resource_count DESC LIMIT 5",
                (rs, i) -> ExploreHubData.ContributorItem.builder()
                        .userId(rs.getLong("created_by"))
                        .username(rs.getString("real_name"))
                        .avatar(rs.getString("head_image"))
                        .resourceCount(rs.getLong("resource_count"))
                        .totalCalls(rs.getLong("total_calls"))
                        .weeklyNewResources(rs.getLong("weekly_new_resources"))
                        .weeklyCalls(rs.getLong("weekly_calls"))
                        .likeCount(rs.getLong("like_count"))
                        .build());

        return ExploreHubData.builder()
                .platformStats(platformStats)
                .trendingResources(trending)
                .recentPublished(recentPublished)
                .recommendedForUser(recommended)
                .announcements(announcements)
                .topContributors(topContributors)
                .build();
    }

    private List<Map<String, Object>> buildDailyTrend(String sql, String valueFieldName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        Map<LocalDate, Long> valueMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object dayObj = row.get("day");
            if (dayObj == null) {
                continue;
            }
            LocalDate day = dayObj instanceof java.sql.Date date ? date.toLocalDate() : LocalDate.parse(String.valueOf(dayObj));
            valueMap.put(day, numberToLong(row.get("cnt")));
        }
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", day.toString());
            item.put(valueFieldName, valueMap.getOrDefault(day, 0L));
            result.add(item);
        }
        return result;
    }

    private String findPreferredTypeByRecentUse(Long userId) {
        if (userId == null) {
            return null;
        }
        List<String> rows = jdbcTemplate.query(
                "SELECT type FROM t_usage_record WHERE user_id = ? ORDER BY create_time DESC LIMIT 1",
                (rs, i) -> rs.getString("type"),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String findPreferredTypeByFavorite(Long userId) {
        if (userId == null) {
            return null;
        }
        List<String> rows = jdbcTemplate.query(
                "SELECT target_type FROM t_favorite WHERE user_id = ? GROUP BY target_type ORDER BY COUNT(*) DESC LIMIT 1",
                (rs, i) -> rs.getString("target_type"),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String buildRecommendationReason(String resourceType, String preferredTypeByRecentUse, String preferredTypeByFavorite) {
        if (StringUtils.hasText(resourceType) && resourceType.equalsIgnoreCase(preferredTypeByRecentUse)) {
            return "你最近使用了同类 " + resourceType.toUpperCase();
        }
        if (StringUtils.hasText(resourceType) && resourceType.equalsIgnoreCase(preferredTypeByFavorite)) {
            return "与你收藏同类资源";
        }
        return "近期发布，与你的浏览偏好接近";
    }

    private static long numberToLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0L;
        }
    }

    /**
     * 今日 0–23 时完整 24 桶（无调用为 0），避免前端把「无桶」误判为「接口无数据」。
     */
    private List<Map<String, Object>> buildCallTrendToday24h() {
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                "SELECT HOUR(create_time) AS hour, COUNT(*) AS cnt "
                        + "FROM t_call_log WHERE DATE(create_time) = CURDATE() "
                        + "GROUP BY HOUR(create_time) ORDER BY hour");
        Map<Integer, Long> byHour = new HashMap<>();
        for (Map<String, Object> row : raw) {
            Object h = row.get("hour");
            if (h == null) {
                continue;
            }
            byHour.put(((Number) h).intValue(), numberToLong(row.get("cnt")));
        }
        List<Map<String, Object>> out = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            long c = byHour.getOrDefault(hour, 0L);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour", hour);
            m.put("calls", c);
            m.put("cnt", c);
            out.add(m);
        }
        return out;
    }

    private static String normalizeHealthStatusToken(Object raw) {
        if (raw == null) {
            return "degraded";
        }
        String s = String.valueOf(raw).trim().toLowerCase();
        if ("healthy".equals(s) || "up".equals(s)) {
            return "healthy";
        }
        if ("down".equals(s) || "offline".equals(s) || "unhealthy".equals(s)) {
            return "down";
        }
        if ("degraded".equals(s) || "warning".equals(s) || "unknown".equals(s)) {
            return "degraded";
        }
        return "degraded";
    }

    private boolean pingDbFast() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 管理概览「系统健康」：优先展示已配置的资源健康项；若表为空则回退到基础设施探测，避免整块空白。
     */
    private List<Map<String, Object>> buildSystemHealthForAdminOverview() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(display_name), ''), resource_code) AS name, health_status "
                        + "FROM t_resource_health_config ORDER BY id ASC LIMIT 30");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String comp = String.valueOf(row.get("name"));
            if (!StringUtils.hasText(comp)) {
                continue;
            }
            String st = normalizeHealthStatusToken(row.get("health_status"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("component", comp);
            m.put("name", comp);
            m.put("status", st);
            m.put("health_status", st);
            out.add(m);
        }
        if (!out.isEmpty()) {
            return out;
        }
        Map<String, Object> dbRow = new LinkedHashMap<>();
        dbRow.put("component", "数据库 (MySQL)");
        dbRow.put("name", "数据库 (MySQL)");
        String dbSt = pingDbFast() ? "healthy" : "down";
        dbRow.put("status", dbSt);
        dbRow.put("health_status", dbSt);
        out.add(dbRow);
        Map<String, Object> apiRow = new LinkedHashMap<>();
        apiRow.put("component", "平台接口服务");
        apiRow.put("name", "平台接口服务");
        apiRow.put("status", "healthy");
        apiRow.put("health_status", "healthy");
        out.add(apiRow);
        return out;
    }

    @Override
    public AdminRealtimeData adminRealtime() {
        Long todayCalls = callLogMapper.selectTodayCount();
        if (todayCalls == null) todayCalls = 0L;

        Long todaySuccess = callLogMapper.selectTodaySuccessCount();
        if (todaySuccess == null) todaySuccess = 0L;
        long todayErrors = todayCalls - todaySuccess;

        Double avgLatency = callLogMapper.selectTodayAvgLatencyMs();

        Long activeUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM t_call_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                Long.class);
        if (activeUsers == null) activeUsers = 0L;

        List<Map<String, Object>> callTrend = buildCallTrendToday24h();

        List<Map<String, Object>> resourceTrend = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS day, COUNT(*) AS cnt "
                        + "FROM t_resource WHERE deleted = 0 AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
                        + "GROUP BY DATE(create_time) ORDER BY day");

        List<Map<String, Object>> userGrowth = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS day, COUNT(*) AS cnt "
                        + "FROM t_user WHERE deleted = 0 AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) "
                        + "GROUP BY DATE(create_time) ORDER BY day");

        Long pendingAudits = auditItemMapper.selectCount(
                new LambdaQueryWrapper<AuditItem>().eq(AuditItem::getStatus, "pending"));
        if (pendingAudits == null) pendingAudits = 0L;

        Long activeAlerts = alertRecordMapper.selectCount(
                new LambdaQueryWrapper<AlertRecord>().eq(AlertRecord::getStatus, "firing"));
        if (activeAlerts == null) activeAlerts = 0L;

        List<Map<String, Object>> topRaw = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') AS resource_type, agent_name, "
                        + "COUNT(*) AS cnt, SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_calls "
                        + "FROM t_call_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
                        + "GROUP BY COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown'), agent_name "
                        + "ORDER BY cnt DESC LIMIT 12");
        List<Map<String, Object>> topResources = new ArrayList<>();
        for (Map<String, Object> row : topRaw) {
            long cnt = numberToLong(row.get("cnt"));
            long ok = numberToLong(row.get("success_calls"));
            double rate = cnt > 0 ? Math.round(ok * 1000.0 / cnt) / 10.0 : 0.0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", row.get("agent_name"));
            m.put("agent_name", row.get("agent_name"));
            m.put("type", row.get("resource_type"));
            m.put("resource_type", row.get("resource_type"));
            m.put("calls", cnt);
            m.put("cnt", cnt);
            m.put("successRate", rate);
            topResources.add(m);
        }

        List<Map<String, Object>> systemHealth = buildSystemHealthForAdminOverview();

        Map<String, Long> publishedResourceCounts = new LinkedHashMap<>();
        publishedResourceCounts.put("agent", countPublishedResourceByType("agent"));
        publishedResourceCounts.put("skill", countPublishedResourceByType("skill"));
        publishedResourceCounts.put("mcp", countPublishedResourceByType("mcp"));
        publishedResourceCounts.put("app", countPublishedResourceByType("app"));
        publishedResourceCounts.put("dataset", countPublishedResourceByType("dataset"));

        List<Map<String, Object>> callsByResourceType7d = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') AS type, COUNT(*) AS calls "
                        + "FROM t_call_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
                        + "GROUP BY COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') ORDER BY calls DESC");

        return AdminRealtimeData.builder()
                .todayCalls(todayCalls)
                .todayErrors(todayErrors)
                .avgLatencyMs(avgLatency)
                .activeUsers(activeUsers)
                .callTrend(callTrend)
                .resourceTrend(resourceTrend)
                .userGrowth(userGrowth)
                .pendingAudits(pendingAudits)
                .activeAlerts(activeAlerts)
                .topResourcesByCall(topResources)
                .systemHealth(systemHealth)
                .publishedResourceCounts(publishedResourceCounts)
                .callsByResourceType7d(callsByResourceType7d)
                .build();
    }

    @Override
    public UserDashboardData userDashboard(Long userId) {
        String uid = String.valueOf(userId);

        Map<String, Object> quotaUsage = new LinkedHashMap<>();
        Map<String, Object> quotaRow = null;
        try {
            quotaRow = jdbcTemplate.queryForMap(
                    "SELECT daily_limit, daily_used, monthly_limit, monthly_used FROM t_quota "
                            + "WHERE target_type = 'user' AND target_id = ? AND enabled = 1 "
                            + "AND resource_category = 'all' ORDER BY id LIMIT 1",
                    userId);
        } catch (Exception ignored) {
        }
        if (quotaRow != null) {
            quotaUsage.put("dailyLimit", quotaRow.getOrDefault("daily_limit", -1));
            quotaUsage.put("dailyUsed", quotaRow.getOrDefault("daily_used", 0));
            quotaUsage.put("monthlyLimit", quotaRow.getOrDefault("monthly_limit", -1));
            quotaUsage.put("monthlyUsed", quotaRow.getOrDefault("monthly_used", 0));
        } else {
            quotaUsage.put("dailyLimit", -1);
            quotaUsage.put("dailyUsed", 0);
            quotaUsage.put("monthlyLimit", -1);
            quotaUsage.put("monthlyUsed", 0);
        }

        Map<String, Object> myResources = new LinkedHashMap<>();
        List<Map<String, Object>> statusCounts = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM t_resource "
                        + "WHERE deleted = 0 AND created_by = ? GROUP BY status",
                userId);
        long draft = 0, pendingReview = 0, published = 0, total = 0;
        for (Map<String, Object> sc : statusCounts) {
            String s = String.valueOf(sc.get("status"));
            long c = ((Number) sc.get("cnt")).longValue();
            total += c;
            switch (s) {
                case "draft" -> draft += c;
                case "pending_review" -> pendingReview += c;
                case "published" -> published += c;
            }
        }
        myResources.put("draft", draft);
        myResources.put("pendingReview", pendingReview);
        myResources.put("published", published);
        myResources.put("total", total);

        List<UsageRecord> recentRows = usageRecordMapper.selectList(
                new LambdaQueryWrapper<UsageRecord>()
                        .eq(UsageRecord::getUserId, userId)
                        .orderByDesc(UsageRecord::getCreateTime)
                        .last("LIMIT 10"));
        List<Map<String, Object>> recentActivity = new ArrayList<>();
        for (UsageRecord r : recentRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("action", r.getAction() != null ? r.getAction() : "invoke");
            row.put("resourceType", r.getType());
            row.put("resourceName", r.getDisplayName() != null ? r.getDisplayName() : r.getAgentName());
            row.put("timestamp", r.getCreateTime());
            recentActivity.add(row);
        }

        Long unread = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false));

        return UserDashboardData.builder()
                .quotaUsage(quotaUsage)
                .myResources(myResources)
                .recentActivity(recentActivity)
                .unreadNotifications(unread != null ? unread : 0L)
                .build();
    }

    /**
     * 全量（未删除）按类型计数；用于管理端总览等需含草稿/待审的场景。
     */
    private long countResourceByType(String type) {
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_resource WHERE deleted = 0 AND resource_type = ?",
                Long.class, type);
        return cnt == null ? 0L : cnt;
    }

    /**
     * 已上架（published）且未删除；与市场对用户可见目录一致。
     */
    private long countPublishedResourceByType(String type) {
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_resource WHERE deleted = 0 AND resource_type = ? AND status = 'published'",
                Long.class, type);
        return cnt == null ? 0L : cnt;
    }

    private int parseDays(String range) {
        int days = 7;
        if (range.endsWith("d")) {
            try {
                days = Math.max(1, Math.min(90, Integer.parseInt(range.replace("d", ""))));
            } catch (NumberFormatException ignored) {
                days = 7;
            }
        }
        return days;
    }
}
