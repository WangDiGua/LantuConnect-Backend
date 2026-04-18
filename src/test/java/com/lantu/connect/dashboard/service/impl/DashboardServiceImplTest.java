package com.lantu.connect.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.audit.mapper.AuditItemMapper;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.dashboard.dto.AdminOverviewVO;
import com.lantu.connect.dashboard.dto.UsageStatsVO;
import com.lantu.connect.dashboard.dto.AdminRealtimeData;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.CircuitBreakerMapper;
import com.lantu.connect.monitoring.mapper.HealthConfigMapper;
import com.lantu.connect.notification.mapper.NotificationMapper;
import com.lantu.connect.sysconfig.mapper.AnnouncementMapper;
import com.lantu.connect.useractivity.mapper.FavoriteMapper;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private CallLogMapper callLogMapper;
    @Mock
    private AuditItemMapper auditItemMapper;
    @Mock
    private UsageRecordMapper usageRecordMapper;
    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private HealthConfigMapper healthConfigMapper;
    @Mock
    private CircuitBreakerMapper circuitBreakerMapper;
    @Mock
    private AlertRecordMapper alertRecordMapper;
    @Mock
    private AnnouncementMapper announcementMapper;

    @InjectMocks
    private DashboardServiceImpl service;

    @Test
    void adminOverviewShouldCountPendingReviewAudits() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(callLogMapper.selectTodayCount()).thenReturn(0L);
        when(callLogMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(String.class))).thenReturn(0L);
        when(auditItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        AdminOverviewVO overview = service.adminOverview(1L);

        assertEquals(5L, ((Number) overview.getSummary().get("pendingAudits")).longValue());
    }

    @Test
    void adminRealtimeShouldCountPendingReviewAudits() {
        when(callLogMapper.selectTodayCount()).thenReturn(0L);
        when(callLogMapper.selectTodaySuccessCount()).thenReturn(0L);
        when(callLogMapper.selectTodayAvgLatencyMs()).thenReturn(12.5D);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(String.class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(any(String.class))).thenReturn(List.of());
        when(alertRecordMapper.selectCount(any())).thenReturn(0L);
        when(auditItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(6L);
        when(healthConfigMapper.selectCount(any())).thenReturn(0L);
        when(circuitBreakerMapper.selectCount(any())).thenReturn(0L);

        AdminRealtimeData realtime = service.adminRealtime();

        assertEquals(6L, realtime.getPendingAudits());
    }

    @Test
    void usageStatsShouldExposeDepartmentOwnerAndResourceBreakdowns() {
        when(callLogMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());
        when(callLogMapper.selectTodayCount()).thenReturn(18L);
        doReturn(List.of(Map.of("resource_type", "agent", "calls", 12L, "success_calls", 10L)))
                .when(jdbcTemplate)
                .queryForList(
                        argThat((String sql) -> sql.contains("GROUP BY COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown')")),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class));
        doReturn(List.of(Map.of("department_key", 1001L, "users", 3L, "calls", 12L)))
                .when(jdbcTemplate)
                .queryForList(
                        argThat((String sql) -> sql.contains("GROUP BY COALESCE(u.school_id, -1)")),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class));
        doReturn(List.of(Map.of(
                "owner_user_id", 7L,
                "owner_name", "Alice",
                "calls", 12L,
                "success_calls", 10L,
                "resource_count", 2L)))
                .when(jdbcTemplate)
                .queryForList(
                        argThat((String sql) -> sql.contains("LEFT JOIN t_user u ON u.user_id = r.created_by")
                                && sql.contains("COALESCE(r.created_by, 0) AS owner_user_id")),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class));
        doReturn(List.of(Map.of(
                "resource_type", "agent",
                "agent_name", "Agent Alpha",
                "calls", 12L,
                "success_calls", 10L)))
                .when(jdbcTemplate)
                .queryForList(
                        argThat((String sql) -> sql.contains("GROUP BY COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown'), agent_name")),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class));

        UsageStatsVO stats = service.usageStats("7d");

        Map<String, Object> breakdown = stats.getBreakdown();
        assertTrue(breakdown.containsKey("departmentUsage"));
        assertTrue(breakdown.containsKey("ownerUsage"));
        assertTrue(breakdown.containsKey("topResources"));
    }

    @Test
    void usageStatsBuildsOwnerUsageFromCreatedByAndUserProfile() {
        when(callLogMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());
        when(callLogMapper.selectTodayCount()).thenReturn(0L);
        doReturn(List.of())
                .when(jdbcTemplate)
                .queryForList(
                        any(String.class),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class),
                        org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class));

        service.usageStats("7d");

        org.mockito.Mockito.verify(jdbcTemplate).queryForList(
                argThat((String sql) -> sql.contains("LEFT JOIN t_user u ON u.user_id = r.created_by")
                        && sql.contains("COALESCE(r.created_by, 0) AS owner_user_id")
                        && !sql.contains("r.owner_id")
                        && !sql.contains("r.owner_name")),
                org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class));
    }
}
