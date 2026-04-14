package com.lantu.connect.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.audit.mapper.AuditItemMapper;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.dashboard.dto.AdminOverviewVO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
}
