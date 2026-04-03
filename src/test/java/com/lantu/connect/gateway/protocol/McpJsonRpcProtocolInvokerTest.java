package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.config.IntegrationProperties;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpJsonRpcProtocolInvokerTest {

    @Test
    void rejectsInsecureHttpEndpointByDefault() {
        McpStreamSessionStore store = org.mockito.Mockito.mock(McpStreamSessionStore.class);
        McpOutboundHeaderBuilder headerBuilder = org.mockito.Mockito.mock(McpOutboundHeaderBuilder.class);
        RuntimeAppConfigService runtime = mock(RuntimeAppConfigService.class);
        IntegrationProperties integration = new IntegrationProperties();
        integration.setMcpHttpAccept("application/json");
        integration.setMcpAllowHttp(false);
        integration.setMcpMaxRedirects(0);
        when(runtime.integration()).thenReturn(integration);

        McpJsonRpcProtocolInvoker invoker = new McpJsonRpcProtocolInvoker(new ObjectMapper(), store, headerBuilder, runtime);

        assertThrows(BusinessException.class, () ->
                invoker.invoke("http://example.com/mcp", 5, "trace-1", Map.of(), Map.of(), null));
    }

    @Test
    void rejectsLocalhostEndpointEvenWhenHttpAllowed() {
        McpStreamSessionStore store = org.mockito.Mockito.mock(McpStreamSessionStore.class);
        McpOutboundHeaderBuilder headerBuilder = org.mockito.Mockito.mock(McpOutboundHeaderBuilder.class);
        RuntimeAppConfigService runtime = mock(RuntimeAppConfigService.class);
        IntegrationProperties integration = new IntegrationProperties();
        integration.setMcpHttpAccept("application/json");
        integration.setMcpAllowHttp(true);
        integration.setMcpAllowLocalTargets(false);
        integration.setMcpMaxRedirects(0);
        when(runtime.integration()).thenReturn(integration);

        McpJsonRpcProtocolInvoker invoker = new McpJsonRpcProtocolInvoker(new ObjectMapper(), store, headerBuilder, runtime);

        assertThrows(BusinessException.class, () ->
                invoker.invoke("http://localhost:8080/mcp", 5, "trace-1", Map.of(), Map.of(), null));
    }
}
