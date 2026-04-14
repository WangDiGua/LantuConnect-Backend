package com.lantu.connect.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
import com.lantu.connect.monitoring.ResourceCircuitHealthBridge;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.realtime.RealtimePushService;
import com.lantu.connect.task.support.TaskDistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpAutoHealthProbeTaskTest {

    @Mock
    private TaskDistributedLock taskDistributedLock;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private McpConnectivityProbeService mcpConnectivityProbeService;

    @Mock
    private RealtimePushService realtimePushService;

    @Mock
    private ResourceCircuitHealthBridge resourceCircuitHealthBridge;

    @Mock
    private ResourceHealthService resourceHealthService;

    @Test
    void run_should_refresh_callability_and_persist_probe_timestamps_for_mcp() {
        McpAutoHealthProbeTask task = new McpAutoHealthProbeTask(
                taskDistributedLock,
                jdbcTemplate,
                mcpConnectivityProbeService,
                new ObjectMapper(),
                realtimePushService,
                resourceCircuitHealthBridge,
                resourceHealthService);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("resource_id", 66L);
        target.put("resource_type", "mcp");
        target.put("resource_code", "wenshu-MCP");
        target.put("display_name", "问数-mcp");
        target.put("endpoint", "http://192.168.2.27:3000/mcp");
        target.put("protocol", "http");
        target.put("auth_type", "none");
        target.put("auth_config", Map.of("transport", "http"));

        Map<String, Object> healthRow = new LinkedHashMap<>();
        healthRow.put("id", 7L);
        healthRow.put("check_type", "mcp_jsonrpc");
        healthRow.put("health_status", "health_down");
        healthRow.put("current_state", "CLOSED");
        healthRow.put("consecutive_success", 0L);
        healthRow.put("consecutive_failure", 2L);
        healthRow.put("last_success_at", LocalDateTime.of(2026, 4, 14, 10, 0));
        healthRow.put("last_failure_at", LocalDateTime.of(2026, 4, 14, 10, 5));
        healthRow.put("last_failure_reason", "old failure");
        healthRow.put("healthy_threshold", 3);
        healthRow.put("timeout_sec", 20);

        when(taskDistributedLock.tryLock("McpAutoHealthProbe")).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM t_resource r INNER JOIN t_resource_mcp_ext")) {
                return List.of(target);
            }
            if (sql.contains("FROM t_resource_runtime_policy")) {
                return List.of(healthRow);
            }
            return List.of();
        });
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        when(mcpConnectivityProbeService.probe(any())).thenReturn(McpConnectivityProbeResult.builder()
                .ok(true)
                .statusCode(200)
                .latencyMs(31L)
                .message("ok")
                .bodyPreview("{\"jsonrpc\":\"2.0\",\"result\":{}}")
                .build());

        task.run();

        verify(resourceCircuitHealthBridge).resetOpenOrHalfOpenAfterHealthyProbe("mcp", 66L);
        verify(resourceHealthService).refreshCallability(66L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("last_probe_at"));
        assertTrue(sql.contains("last_check_time"));
        assertTrue(sql.contains("last_success_at"));
        assertTrue(sql.contains("last_failure_at"));
        assertTrue(sql.contains("last_failure_reason"));
        assertTrue(sql.contains("consecutive_success"));
        assertTrue(sql.contains("consecutive_failure"));
    }
}
