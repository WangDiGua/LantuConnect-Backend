package com.lantu.connect.monitoring.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.service.GatewayBindingExpansionService;
import com.lantu.connect.monitoring.ResourceCircuitHealthBridge;
import com.lantu.connect.monitoring.probe.ResourceProbeEngine;
import com.lantu.connect.monitoring.probe.ResourceProbeResult;
import com.lantu.connect.realtime.RealtimePushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceHealthServiceImplTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock GatewayBindingExpansionService gatewayBindingExpansionService;
    @Mock ResourceCircuitHealthBridge resourceCircuitHealthBridge;
    @Mock RealtimePushService realtimePushService;
    @Mock ResourceProbeEngine resourceProbeEngine;

    @Test
    void probeAndPersistClosesOpenCircuitWhenProbeTurnsHealthy() {
        ResourceHealthServiceImpl service = new ResourceHealthServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                gatewayBindingExpansionService,
                resourceCircuitHealthBridge,
                realtimePushService,
                resourceProbeEngine);

        Map<String, Object> resource = Map.of(
                "id", 71L,
                "resource_type", "agent",
                "resource_code", "dify-course-agent",
                "display_name", "dify agent",
                "status", "published");
        Map<String, Object> policy = Map.ofEntries(
                entry("id", 701L),
                entry("health_status", "down"),
                entry("current_state", "OPEN"),
                entry("last_failure_reason", "previous probe failed"),
                entry("consecutive_success", 0L),
                entry("consecutive_failure", 2L),
                entry("interval_sec", 60),
                entry("healthy_threshold", 1),
                entry("timeout_sec", 3),
                entry("failure_threshold", 5),
                entry("open_duration_sec", 60),
                entry("half_open_max_calls", 3),
                entry("probe_strategy", "agent_provider"),
                entry("check_type", "provider"),
                entry("check_url", "https://api.dify.ai/v1/chat-messages"));
        Map<String, Object> detail = Map.of(
                "enabled", 1,
                "registration_protocol", "openai_compatible",
                "upstream_endpoint", "https://api.dify.ai/v1/chat-messages");

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM t_resource_runtime_policy")) {
                return List.of(policy);
            }
            if (sql.contains("JSON_EXTRACT(detail_json")) {
                return List.of(detail);
            }
            if (sql.contains("FROM t_resource")) {
                return List.of(resource);
            }
            return List.of();
        });
        when(gatewayBindingExpansionService.listAgentBoundMcpIds(71L)).thenReturn(List.of());
        when(resourceProbeEngine.probe(any())).thenReturn(new ResourceProbeResult(
                "healthy",
                "agent_provider",
                "probe ok",
                null,
                80L,
                "200 OK",
                Map.of("ok", true)));

        service.probeAndPersist(71L);

        verify(resourceCircuitHealthBridge).resetOpenOrHalfOpenAfterHealthyProbe("agent", 71L);
    }
}
