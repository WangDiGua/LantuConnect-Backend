package com.lantu.connect.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.config.GatewayInvokeProperties;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.gateway.support.GatewayCallerResolver;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpHttpGatewayControllerWebMvcTest {

    @Mock
    private UnifiedGatewayService unifiedGatewayService;

    @Mock
    private ApiKeyScopeService apiKeyScopeService;

    @Mock
    private GatewayCallerResolver gatewayCallerResolver;

    @Mock
    private RuntimeAppConfigService runtimeAppConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private McpHttpGatewayController controller;

    @BeforeEach
    void setUp() {
        GatewayInvokeProperties gw = new GatewayInvokeProperties();
        gw.setInvokeHttpStatusReflectsUpstream(false);
        lenient().when(runtimeAppConfigService.gateway()).thenReturn(gw);
        controller = new McpHttpGatewayController(
                unifiedGatewayService,
                apiKeyScopeService,
                gatewayCallerResolver,
                runtimeAppConfigService,
                objectMapper);
    }

    @Test
    void rejectsMissingApiKeyWith401JsonRpc() throws Exception {
        when(apiKeyScopeService.authenticateOrNull(null)).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        ResponseEntity<?> res = controller.mcpMessage(
                "mcp", "42", null, null, null, null, null,
                objectMapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}"), req);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
        assertTrue(String.valueOf(res.getBody()).contains("\"error\""));
    }

    @Test
    void forwardsPayloadToInvoke() throws Exception {
        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("secret")).thenReturn(apiKey);
        when(gatewayCallerResolver.resolveTrustedUserIdOrNull()).thenReturn(null);
        when(unifiedGatewayService.invoke(any(), any(), any(), any(), eq(apiKey)))
                .thenReturn(InvokeResponse.builder()
                        .status("success")
                        .statusCode(200)
                        .body("{\"jsonrpc\":\"2.0\",\"id\":\"t1\",\"result\":{}}")
                        .build());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        ResponseEntity<?> res = controller.mcpMessage(
                "mcp", "42", null, null, null, null, "secret",
                objectMapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":\"t1\",\"method\":\"initialize\",\"params\":{}}"),
                req);
        assertEquals(HttpStatus.OK, res.getStatusCode());

        ArgumentCaptor<InvokeRequest> cap = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(unifiedGatewayService).invoke(any(), any(), any(), cap.capture(), eq(apiKey));
        assertEquals("mcp", cap.getValue().getResourceType());
        assertEquals("42", cap.getValue().getResourceId());
        assertEquals("initialize", cap.getValue().getPayload().get("method"));
    }

    @Test
    void streamBranchUsesInvokeStream() throws Exception {
        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("secret")).thenReturn(apiKey);
        when(gatewayCallerResolver.resolveTrustedUserIdOrNull()).thenReturn(5L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        ResponseEntity<?> res = controller.mcpMessage(
                "mcp", "9", true, null, null, null, "secret",
                objectMapper.valueToTree(Map.of("method", "tools/list")),
                req);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getHeaders().getContentType() != null
                && res.getHeaders().getContentType().toString().contains("event-stream"));
    }
}
