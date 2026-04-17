package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.monitoring.dto.CallLogDetailVO;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.dto.PerformanceAnalysisVO;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        assertNotNull(result.getCompareSummary());
        assertEquals(result.getSlowMethods(), result.getMethodLeaderboard());
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

    @Test
    void callLogDetailProjectsTraceAlertAndHealthEvidence() {
        CallLog log = new CallLog();
        log.setId("log-1");
        log.setTraceId("trace-1");
        log.setAgentId("42");
        log.setAgentName("Agent Alpha");
        log.setResourceType("agent");
        log.setUserId("9");
        log.setMethod("POST /invoke");
        log.setStatus("error");
        log.setStatusCode(500);
        log.setLatencyMs(812);
        log.setErrorMessage("tool timeout");

        when(callLogMapper.selectById("log-1")).thenReturn(log);
        when(userDisplayNameResolver.resolveDisplayNames(List.of(9L))).thenReturn(Map.of(9L, "ops-user"));
        doReturn(List.of(traceDetailRow()))
                .doReturn(List.of(alertEvidenceRow()))
                .doReturn(List.of(resourceHealthRow()))
                .when(jdbcTemplate)
                .queryForList(anyString(), any(Object[].class));

        CallLogDetailVO detail = monitoringService.callLogDetail("log-1");

        assertEquals("log-1", detail.getLog().getId());
        assertEquals("ops-user", detail.getLog().getUsername());
        assertNotNull(detail.getTrace());
        assertEquals("trace-1", detail.getTrace().getTraceId());
        assertEquals(1, detail.getRelatedAlerts().size());
        assertEquals("Alert: tool timeout", detail.getRelatedAlerts().get(0).getMessage());
        assertNotNull(detail.getResourceHealth());
        assertEquals("degraded", detail.getResourceHealth().getHealthStatus());
        assertEquals("dependency_blocked", detail.getResourceHealth().getCallabilityState());
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

    private static Map<String, Object> traceDetailRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("requestId", "req-1");
        row.put("rootOperation", "gateway.invoke");
        row.put("entryService", "unified-gateway");
        row.put("rootResourceType", "agent");
        row.put("rootResourceId", 42L);
        row.put("rootResourceCode", "agent.alpha");
        row.put("rootDisplayName", "Agent Alpha");
        row.put("status", "error");
        row.put("startedAt", LocalDateTime.of(2026, 4, 17, 10, 0));
        row.put("durationMs", 812);
        row.put("spanCount", 4);
        row.put("errorSpanCount", 1);
        row.put("firstErrorMessage", "tool timeout");
        row.put("userId", 9L);
        row.put("ip", "127.0.0.1");
        row.put("traceId", "trace-1");
        return row;
    }

    private static Map<String, Object> alertEvidenceRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "alert-1");
        row.put("ruleId", "rule-1");
        row.put("ruleName", "gateway error rate");
        row.put("severity", "critical");
        row.put("status", "firing");
        row.put("message", "Alert: tool timeout");
        row.put("firedAt", LocalDateTime.of(2026, 4, 17, 10, 1));
        return row;
    }

    private static Map<String, Object> resourceHealthRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", 42L);
        row.put("resourceType", "agent");
        row.put("resourceCode", "agent.alpha");
        row.put("displayName", "Agent Alpha");
        row.put("healthStatus", "degraded");
        row.put("circuitState", "OPEN");
        row.put("callabilityState", "dependency_blocked");
        row.put("callabilityReason", "dependency blocked");
        row.put("lastFailureReason", "tool timeout");
        row.put("lastFailureAt", LocalDateTime.of(2026, 4, 17, 9, 59));
        return row;
    }
}
