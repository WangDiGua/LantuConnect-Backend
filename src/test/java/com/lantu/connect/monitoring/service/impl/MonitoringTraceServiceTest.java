package com.lantu.connect.monitoring.service.impl;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.dto.TraceDetailVO;
import com.lantu.connect.monitoring.dto.TraceListItemVO;
import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringTraceServiceTest {

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
    void tracesReturnsTraceLevelRowsInsteadOfFlatSpanPage() {
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setPageSize(20);
        query.setStatus("error");
        query.setKeyword("trace-1");

        doReturn(1L)
                .when(jdbcTemplate)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));
        doReturn(List.of(traceListRow()))
                .when(jdbcTemplate)
                .queryForList(anyString(), any(Object[].class));

        PageResult<TraceListItemVO> result = monitoringService.traces(query);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        TraceListItemVO item = result.getList().get(0);
        assertEquals("trace-1", item.getTraceId());
        assertEquals("req-1", item.getRequestId());
        assertEquals("Agent Alpha", item.getRootDisplayName());
        assertEquals(4, item.getSpanCount());
        assertEquals(1, item.getErrorSpanCount());
        assertEquals("tool timeout", item.getFirstErrorMessage());
    }

    @Test
    void tracesWrapsPagedSqlBeforeOrderingByComputedAliases() {
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setPageSize(20);

        doReturn(0L)
                .when(jdbcTemplate)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));
        doReturn(List.of())
                .when(jdbcTemplate)
                .queryForList(anyString(), any(Object[].class));

        monitoringService.traces(query);

        org.mockito.Mockito.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.argThat((String sql) -> sql.contains("SELECT * FROM (")
                        && sql.contains(") trace_page ORDER BY CASE WHEN trace_page.status = 'error' THEN 0 ELSE 1 END ASC, trace_page.startedAt DESC")),
                any(Object[].class));
    }

    @Test
    void traceDetailBuildsSummaryRootCauseAndOrderedSpans() {
        doReturn(List.of(traceDetailRow()))
                .when(jdbcTemplate)
                .queryForList(anyString(), any(Object[].class));

        when(traceSpanMapper.selectList(any())).thenReturn(List.of(
                traceSpan("span-root", "trace-1", null, "gateway.invoke", "unified-gateway",
                        LocalDateTime.of(2026, 4, 17, 10, 0, 0), 812, "error",
                        Map.of("requestId", "req-1", "statusCode", 500), List.of(Map.of("message", "gateway failed"))),
                traceSpan("span-child", "trace-1", "span-root", "mcp.tools/list", "binding-expansion",
                        LocalDateTime.of(2026, 4, 17, 10, 0, 0, 300_000_000), 220, "error",
                        Map.of("errorMessage", "tool timeout", "protocol", "mcp"), List.of(Map.of("message", "tool timeout")))
        ));

        TraceDetailVO detail = monitoringService.traceDetail("trace-1");

        assertNotNull(detail.getSummary());
        assertEquals("trace-1", detail.getSummary().getTraceId());
        assertEquals("req-1", detail.getSummary().getRequestId());
        assertEquals("Agent Alpha", detail.getSummary().getRootDisplayName());
        assertEquals(2, detail.getSpans().size());
        assertEquals("span-root", detail.getSpans().get(0).getId());
        assertEquals("span-child", detail.getRootCause().getSpanId());
        assertTrue(detail.getRootCause().getMessage().contains("tool timeout"));
        assertEquals(1, detail.getCallLogs().size());
    }

    private static TraceSpan traceSpan(String id,
                                       String traceId,
                                       String parentId,
                                       String operationName,
                                       String serviceName,
                                       LocalDateTime startTime,
                                       int duration,
                                       String status,
                                       Map<String, Object> tags,
                                       Object logs) {
        TraceSpan span = new TraceSpan();
        span.setId(id);
        span.setTraceId(traceId);
        span.setParentId(parentId);
        span.setOperationName(operationName);
        span.setServiceName(serviceName);
        span.setStartTime(startTime);
        span.setDuration(duration);
        span.setStatus(status);
        span.setTags(tags);
        span.setLogs(logs);
        return span;
    }

    private static Map<String, Object> traceListRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("traceId", "trace-1");
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
        row.put("userId", 9L);
        row.put("ip", "127.0.0.1");
        row.put("statusCode", 500);
        row.put("errorMessage", "tool timeout");
        row.put("createdAt", LocalDateTime.of(2026, 4, 17, 10, 0, 1));
        return row;
    }
}
