package com.lantu.connect.monitoring.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpProbeHandlerTest {

    @Test
    void probe_should_degrade_when_initialize_succeeds_but_tools_list_is_empty() throws Exception {
        McpConnectivityProbeService mcpConnectivityProbeService = mock(McpConnectivityProbeService.class);
        ProtocolInvokerRegistry protocolInvokerRegistry = mock(ProtocolInvokerRegistry.class);
        when(mcpConnectivityProbeService.probe(any()))
                .thenReturn(McpConnectivityProbeResult.builder()
                        .ok(true)
                        .statusCode(200)
                        .latencyMs(35L)
                        .message("ok")
                        .bodyPreview("{\"jsonrpc\":\"2.0\",\"result\":{}}")
                        .build());
        when(protocolInvokerRegistry.invoke(
                eq("mcp"),
                eq("https://mcp.example.com"),
                anyInt(),
                anyString(),
                anyMap(),
                anyMap(),
                any()))
                .thenReturn(new ProtocolInvokeResult(200, "{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[]}}", 48L));

        McpProbeHandler handler = new McpProbeHandler(mcpConnectivityProbeService, protocolInvokerRegistry, new ObjectMapper());

        ResourceProbeTarget target = ResourceProbeTarget.builder()
                .resourceId(55L)
                .resourceType("mcp")
                .resourceCode("demo-mcp")
                .displayName("Demo MCP")
                .endpoint("https://mcp.example.com")
                .protocol("http")
                .authType("none")
                .authConfig(Map.of("transport", "http"))
                .timeoutSec(20)
                .build();

        ResourceProbeResult result = handler.probe(target);

        assertEquals("degraded", result.healthStatus());
        assertEquals("mcp_jsonrpc", result.probeStrategy());
        assertEquals("mcp tools/list returned no tools", result.failureReason());
    }
}
