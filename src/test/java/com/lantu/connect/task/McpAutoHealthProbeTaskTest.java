package com.lantu.connect.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
import com.lantu.connect.monitoring.ResourceCircuitHealthBridge;
import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
    void run_should_delegate_mcp_probe_to_resource_health_service() {
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

        when(taskDistributedLock.tryLock("McpAutoHealthProbe")).thenReturn(true);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(66L));
        when(resourceHealthService.probeAndPersist(66L)).thenReturn(ResourceHealthSnapshotVO.builder()
                .resourceId(66L)
                .resourceType("mcp")
                .resourceCode("wenshu-MCP")
                .displayName("闂暟-mcp")
                .healthStatus("healthy")
                .build());

        task.run();

        verify(resourceHealthService).probeAndPersist(66L);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
