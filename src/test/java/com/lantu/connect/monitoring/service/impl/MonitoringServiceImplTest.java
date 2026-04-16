package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.monitoring.dto.PerformanceAnalysisVO;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonitoringServiceImplTest {

    @Mock
    private CallLogMapper callLogMapper;
    @Mock
    private AlertRecordMapper alertRecordMapper;
    @Mock
    private TraceSpanMapper traceSpanMapper;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private MonitoringServiceImpl monitoringService;

    @Test
    void callLogsAppliesStatusFilter() {
        when(callLogMapper.selectPage(any(), ArgumentMatchers.argThat(w -> true)))
                .thenReturn(new Page<>(1, 10));
        PageQuery query = new PageQuery();
        query.setStatus("error");
        monitoringService.callLogs(query);
        verify(callLogMapper).selectPage(any(), any());
    }

    @Test
    void callLogsSkipsStatusWhenAll() {
        when(callLogMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        PageQuery query = new PageQuery();
        query.setStatus("all");
        monitoringService.callLogs(query);
        verify(callLogMapper).selectPage(any(), any());
    }

    @Test
    void alertsAppliesSeverityAndAlertStatus() {
        when(alertRecordMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        PageQuery query = new PageQuery();
        query.setSeverity("critical");
        query.setAlertStatus("firing");
        monitoringService.alerts(query);
        verify(alertRecordMapper).selectPage(any(), any());
    }

    @Test
    void alertsSkipsAlertStatusWhenAll() {
        when(alertRecordMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        PageQuery query = new PageQuery();
        query.setAlertStatus("all");
        monitoringService.alerts(query);
        verify(alertRecordMapper).selectPage(any(), any());
    }

    @Test
    void qualityHistoryPassesResourceTypeToJdbc() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 3, 31, 12, 0);
        when(jdbcTemplate.queryForList(anyString(), eq("42"), eq("skill"), eq("skill"), eq(from), eq(to)))
                .thenReturn(Collections.emptyList());
        monitoringService.qualityHistory("skill", 42L, from, to);
        verify(jdbcTemplate).queryForList(
                ArgumentMatchers.contains("resource_type"),
                eq("42"),
                eq("skill"),
                eq("skill"),
                eq(from),
                eq(to));
    }

    @Test
    void performanceAnalysisComputesRealPercentilesAndRates() {
        LocalDateTime now = LocalDateTime.now();
        doReturn(List.of(
                sample(now.minusMinutes(12), "mcp", "101", "HowToCook MCP", "tools/list", "success", 100),
                sample(now.minusMinutes(11), "mcp", "101", "HowToCook MCP", "tools/list", "success", 200),
                sample(now.minusMinutes(10), "mcp", "101", "HowToCook MCP", "tools/list", "error", 300),
                sample(now.minusMinutes(9), "mcp", "101", "HowToCook MCP", "tools/list", "timeout", 900)
        )).when(jdbcTemplate).queryForList(anyString());

        PerformanceAnalysisVO result = monitoringService.performanceAnalysis("24h", "mcp", 101L);

        assertEquals(4L, result.getSummary().getRequestCount());
        assertEquals(2L, result.getSummary().getSuccessCount());
        assertEquals(2L, result.getSummary().getErrorCount());
        assertEquals(1L, result.getSummary().getTimeoutCount());
        assertEquals(0.5D, result.getSummary().getSuccessRate(), 0.0001D);
        assertEquals(0.5D, result.getSummary().getErrorRate(), 0.0001D);
        assertEquals(0.25D, result.getSummary().getTimeoutRate(), 0.0001D);
        assertEquals(200D, result.getSummary().getP50LatencyMs(), 0.0001D);
        assertEquals(900D, result.getSummary().getP95LatencyMs(), 0.0001D);
        assertEquals(900D, result.getSummary().getP99LatencyMs(), 0.0001D);
        assertEquals(24, result.getBuckets().size());
        assertEquals(1, result.getResourceLeaderboard().size());
        assertTrue(result.getResourceLeaderboard().get(0).isLowSample());
        assertEquals("HowToCook MCP", result.getResourceLeaderboard().get(0).getResourceName());
        assertEquals(1, result.getSlowMethods().size());
        assertEquals("tools/list", result.getSlowMethods().get(0).getMethod());
    }

    @Test
    void performanceAnalysisRespectsWindowAndResourceFilters() {
        LocalDateTime now = LocalDateTime.now();
        doReturn(List.of(
                sample(now.minusDays(1), "agent", "8", "Agent A", "POST /invoke", "success", 120),
                sample(now.minusDays(2), "agent", "8", "Agent A", "POST /invoke", "success", 180),
                sample(now.minusHours(3), "mcp", "7", "MCP A", "tools/list", "success", 90),
                sample(now.minusHours(2), "mcp", "9", "MCP B", "tools/list", "error", 400)
        )).when(jdbcTemplate).queryForList(anyString());

        PerformanceAnalysisVO result = monitoringService.performanceAnalysis("7d", "mcp", 7L);

        assertEquals("7d", result.getWindow());
        assertEquals(7, result.getBuckets().size());
        assertEquals(1L, result.getSummary().getRequestCount());
        assertEquals(1, result.getResourceLeaderboard().size());
        assertEquals(Long.valueOf(7L), result.getResourceLeaderboard().get(0).getResourceId());
        assertEquals("MCP A", result.getResourceLeaderboard().get(0).getResourceName());
    }

    @Test
    void performanceCompatibilityEndpointProjectsBucketPercentiles() {
        LocalDateTime now = LocalDateTime.now();
        doReturn(List.of(
                sample(now.minusMinutes(3), "agent", "42", "Agent Alpha", "POST /invoke", "success", 10),
                sample(now.minusMinutes(2), "agent", "42", "Agent Alpha", "POST /invoke", "success", 1000),
                sample(now.minusMinutes(1), "agent", "42", "Agent Alpha", "POST /invoke", "timeout", 2000),
                sample(now.minusSeconds(30), "agent", "42", "Agent Alpha", "POST /invoke", "success", 3000),
                sample(now.minusSeconds(10), "agent", "42", "Agent Alpha", "POST /invoke", "success", 4000)
        )).when(jdbcTemplate).queryForList(anyString());

        List<Map<String, Object>> buckets = monitoringService.performance("agent");

        assertFalse(buckets.isEmpty());
        Map<String, Object> bucket = buckets.get(buckets.size() - 1);
        assertEquals(5L, ((Number) bucket.get("requestCount")).longValue());
        assertEquals(4000D, ((Number) bucket.get("p99Latency")).doubleValue(), 0.0001D);
        assertEquals(5D, ((Number) bucket.get("throughput")).doubleValue(), 0.0001D);
    }

    @Test
    void callLogLegacyModelFieldDoesNotParticipateInSqlProjection() throws NoSuchFieldException {
        Field field = CallLog.class.getDeclaredField("model");
        TableField tableField = field.getAnnotation(TableField.class);

        assertNotNull(tableField, "legacy model field must declare TableField metadata");
        assertFalse(tableField.exist(), "legacy model field must be excluded from MyBatis SQL");
    }

    private static Map<String, Object> sample(LocalDateTime createTime,
                                              String resourceType,
                                              String resourceId,
                                              String resourceName,
                                              String method,
                                              String status,
                                              int latencyMs) {
        Map<String, Object> row = new HashMap<>();
        row.put("create_time", createTime);
        row.put("resource_type", resourceType);
        row.put("resource_id", resourceId);
        row.put("resource_name", resourceName);
        row.put("method", method);
        row.put("status", status);
        row.put("latency_ms", latencyMs);
        return row;
    }
}
