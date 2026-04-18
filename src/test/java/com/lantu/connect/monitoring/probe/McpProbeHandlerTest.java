package com.lantu.connect.monitoring.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpProbeHandlerTest {

    @Test
    void probe_should_send_initialized_notification_before_tools_list() throws Exception {
        McpConnectivityProbeService mcpConnectivityProbeService = mock(McpConnectivityProbeService.class);
        ProtocolInvokerRegistry protocolInvokerRegistry = mock(ProtocolInvokerRegistry.class);
        when(mcpConnectivityProbeService.probe(any(), any(ProtocolInvokeContext.class)))
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
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = invocation.getArgument(4, Map.class);
                    String method = String.valueOf(payload.get("method"));
                    if ("notifications/initialized".equals(method)) {
                        return new ProtocolInvokeResult(202, "", 5L);
                    }
                    if ("tools/list".equals(method)) {
                        return new ProtocolInvokeResult(200, "{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[{\"name\":\"demo\"}]}}", 48L);
                    }
                    throw new AssertionError("unexpected method: " + method);
                });

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

        assertEquals("healthy", result.healthStatus());
        org.mockito.Mockito.verify(mcpConnectivityProbeService)
                .probe(any(), argThat((ProtocolInvokeContext ctx) -> ctx != null && ctx.apiKeyId() != null && !ctx.apiKeyId().isBlank()));
        InOrder order = inOrder(protocolInvokerRegistry);
        order.verify(protocolInvokerRegistry).invoke(
                eq("mcp"),
                eq("https://mcp.example.com"),
                anyInt(),
                anyString(),
                argThat(hasMethod("notifications/initialized")),
                anyMap(),
                any());
        order.verify(protocolInvokerRegistry).invoke(
                eq("mcp"),
                eq("https://mcp.example.com"),
                anyInt(),
                anyString(),
                argThat(hasMethod("tools/list")),
                anyMap(),
                any());
    }

    @Test
    void probe_should_degrade_when_initialize_succeeds_but_tools_list_is_empty() throws Exception {
        McpConnectivityProbeService mcpConnectivityProbeService = mock(McpConnectivityProbeService.class);
        ProtocolInvokerRegistry protocolInvokerRegistry = mock(ProtocolInvokerRegistry.class);
        when(mcpConnectivityProbeService.probe(any(), any(ProtocolInvokeContext.class)))
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

    private static ArgumentMatcher<Map<String, Object>> hasMethod(String expectedMethod) {
        return payload -> payload != null && expectedMethod.equals(String.valueOf(payload.get("method")));
    }
}
