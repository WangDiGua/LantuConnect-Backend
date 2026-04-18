package com.lantu.connect.gateway.service;

import com.lantu.connect.gateway.dto.McpConnectivityProbeRequest;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.protocol.McpJsonRpcProtocolInvoker;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpConnectivityProbeServiceTest {

    @Test
    void probe_should_forward_context_to_protocol_invoker_for_session_reuse() throws Exception {
        McpJsonRpcProtocolInvoker invoker = mock(McpJsonRpcProtocolInvoker.class);
        McpConnectivityProbeService service = new McpConnectivityProbeService(invoker);
        ProtocolInvokeContext ctx = ProtocolInvokeContext.of("health-probe:mcp:65", 65L, null);

        when(invoker.invoke(
                eq("https://mcp.example.com"),
                anyInt(),
                anyString(),
                anyMap(),
                anyMap(),
                eq(ctx)))
                .thenReturn(new ProtocolInvokeResult(
                        200,
                        "{\"jsonrpc\":\"2.0\",\"id\":\"probe-init\",\"result\":{\"protocolVersion\":\"2024-11-05\"}}",
                        35L));

        McpConnectivityProbeRequest request = new McpConnectivityProbeRequest();
        request.setEndpoint("https://mcp.example.com");
        request.setAuthType("none");
        request.setAuthConfig(Map.of("transport", "http"));
        request.setTransport("http");

        McpConnectivityProbeResult result = service.probe(request, ctx);

        assertTrue(result.isOk());
        assertEquals(200, result.getStatusCode());
        verify(invoker).invoke(
                eq("https://mcp.example.com"),
                anyInt(),
                anyString(),
                anyMap(),
                anyMap(),
                eq(ctx));
    }
}
