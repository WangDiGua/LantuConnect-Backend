package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.security.GatewayAuthDetails;
import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.common.config.GatewayInvokeProperties;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SdkGatewayControllerTest {

    @Mock
    private UnifiedGatewayService unifiedGatewayService;

    @Mock
    private ApiKeyScopeService apiKeyScopeService;

    @Mock
    private RuntimeAppConfigService runtimeAppConfigService;

    @InjectMocks
    private SdkGatewayController sdkGatewayController;

    @BeforeEach
    void wireGatewayFlags() {
        GatewayInvokeProperties gw = new GatewayInvokeProperties();
        gw.setInvokeHttpStatusReflectsUpstream(true);
        lenient().when(runtimeAppConfigService.gateway()).thenReturn(gw);
    }

    @Test
    void shouldRejectWhenApiKeyMissing() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("mcp");
        request.setResourceId("9");
        assertThrows(BusinessException.class, () ->
                sdkGatewayController.invoke(null, " ", request, new MockHttpServletRequest()));
    }

    @Test
    void shouldGenerateTraceIdWhenMissing() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("mcp");
        request.setResourceId("9");
        request.setPayload(Map.of("x", 1));

        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("raw-key")).thenReturn(apiKey);
        when(unifiedGatewayService.invoke(any(), any(), any(), any(), any()))
                .thenReturn(InvokeResponse.builder()
                        .requestId("req-1")
                        .traceId("trace-1")
                        .resourceType("mcp")
                        .resourceId("9")
                        .status("success")
                        .statusCode(200)
                        .latencyMs(9L)
                        .body("{}")
                        .build());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        ResponseEntity<R<InvokeResponse>> response = sdkGatewayController.invoke(null, "raw-key", request, req);
        assertEquals(0, response.getBody().getCode());

        ArgumentCaptor<String> traceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(unifiedGatewayService).invoke(any(), traceIdCaptor.capture(), any(), any(), eq(apiKey));
        String traceId = traceIdCaptor.getValue();
        assertEquals(true, traceId != null && !traceId.isBlank());
    }

    @Test
    void shouldUseTrustedUserIdFromGatewayAuthDetailsWhenApiKeyPrincipal() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("mcp");
        request.setResourceId("9");
        request.setPayload(Map.of("x", 1));
        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("raw-key")).thenReturn(apiKey);
        when(unifiedGatewayService.invoke(any(), any(), any(), any(), any()))
                .thenReturn(InvokeResponse.builder()
                        .requestId("req-1")
                        .traceId("trace-1")
                        .resourceType("mcp")
                        .resourceId("9")
                        .status("success")
                        .statusCode(200)
                        .latencyMs(9L)
                        .body("{}")
                        .build());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("api-key", null, Collections.emptyList());
        auth.setDetails(new GatewayAuthDetails(77L));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("127.0.0.1");
            sdkGatewayController.invoke(null, "raw-key", request, req);
            verify(unifiedGatewayService).invoke(eq(77L), any(), any(), any(), eq(apiKey));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldUseTrustedUserIdFromSecurityContext() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("mcp");
        request.setResourceId("9");
        request.setPayload(Map.of("x", 1));
        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("raw-key")).thenReturn(apiKey);
        when(unifiedGatewayService.invoke(any(), any(), any(), any(), any()))
                .thenReturn(InvokeResponse.builder()
                        .requestId("req-1")
                        .traceId("trace-1")
                        .resourceType("mcp")
                        .resourceId("9")
                        .status("success")
                        .statusCode(200)
                        .latencyMs(9L)
                        .body("{}")
                        .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("88", null));
        try {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("127.0.0.1");
            sdkGatewayController.invoke(null, "raw-key", request, req);
            verify(unifiedGatewayService).invoke(eq(88L), any(), any(), any(), eq(apiKey));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldMapUpstreamFailureToHttpStatusAndBusinessCode() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("mcp");
        request.setResourceId("9");

        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("raw-key")).thenReturn(apiKey);
        when(unifiedGatewayService.invoke(any(), any(), any(), any(), any()))
                .thenReturn(InvokeResponse.builder()
                        .status("error")
                        .statusCode(503)
                        .resourceType("mcp")
                        .resourceId("9")
                        .body("upstream busy")
                        .build());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        ResponseEntity<R<InvokeResponse>> response = sdkGatewayController.invoke(null, "raw-key", request, req);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotEquals(0, response.getBody().getCode());
    }
}
