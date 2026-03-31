package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class McpJsonRpcProtocolInvokerTest {

    @Test
    void rejectsInsecureHttpEndpointByDefault() {
        McpStreamSessionStore store = org.mockito.Mockito.mock(McpStreamSessionStore.class);
        McpOutboundHeaderBuilder headerBuilder = org.mockito.Mockito.mock(McpOutboundHeaderBuilder.class);
        McpJsonRpcProtocolInvoker invoker = new McpJsonRpcProtocolInvoker(new ObjectMapper(), store, headerBuilder);
        ReflectionTestUtils.setField(invoker, "mcpHttpAccept", "application/json");
        ReflectionTestUtils.setField(invoker, "mcpAllowHttp", false);
        ReflectionTestUtils.setField(invoker, "mcpMaxRedirects", 0);

        assertThrows(BusinessException.class, () ->
                invoker.invoke("http://example.com/mcp", 5, "trace-1", Map.of(), Map.of(), null));
    }

    @Test
    void rejectsLocalhostEndpointEvenWhenHttpAllowed() {
        McpStreamSessionStore store = org.mockito.Mockito.mock(McpStreamSessionStore.class);
        McpOutboundHeaderBuilder headerBuilder = org.mockito.Mockito.mock(McpOutboundHeaderBuilder.class);
        McpJsonRpcProtocolInvoker invoker = new McpJsonRpcProtocolInvoker(new ObjectMapper(), store, headerBuilder);
        ReflectionTestUtils.setField(invoker, "mcpHttpAccept", "application/json");
        ReflectionTestUtils.setField(invoker, "mcpAllowHttp", true);
        ReflectionTestUtils.setField(invoker, "mcpMaxRedirects", 0);

        assertThrows(BusinessException.class, () ->
                invoker.invoke("http://localhost:8080/mcp", 5, "trace-1", Map.of(), Map.of(), null));
    }
}
