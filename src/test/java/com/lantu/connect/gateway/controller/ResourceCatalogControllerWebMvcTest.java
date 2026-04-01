package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceResolveRequest;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.gateway.support.GatewayCallerResolver;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceCatalogControllerWebMvcTest {

    @Mock
    private UnifiedGatewayService unifiedGatewayService;

    @Mock
    private ApiKeyScopeService apiKeyScopeService;

    @Mock
    private GatewayCallerResolver gatewayCallerResolver;

    @InjectMocks
    private ResourceCatalogController resourceCatalogController;

    @BeforeEach
    void wireGatewayFlags() {
        ReflectionTestUtils.setField(resourceCatalogController, "invokeHttpStatusReflectsUpstream", true);
    }

    @Test
    void shouldUseRequestIdWhenTraceIdMissing() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("mcp");
        request.setResourceId("1");
        request.setPayload(Map.of("k", "v"));

        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("raw-key")).thenReturn(apiKey);
        when(gatewayCallerResolver.resolveTrustedUserIdOrNull()).thenReturn(null);
        when(unifiedGatewayService.invoke(any(), any(), any(), any(), any()))
                .thenReturn(InvokeResponse.builder()
                        .requestId("req-1")
                        .traceId("trace-from-request-id")
                        .status("success")
                        .statusCode(200)
                        .resourceType("mcp")
                        .resourceId("1")
                        .latencyMs(10L)
                        .body("{}")
                        .build());

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
        ResponseEntity<R<InvokeResponse>> response = resourceCatalogController.invoke(
                null, "request-id-1", "raw-key", request, httpRequest);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().getCode());
        assertEquals("req-1", response.getBody().getData().getRequestId());

        ArgumentCaptor<String> traceCaptor = ArgumentCaptor.forClass(String.class);
        verify(unifiedGatewayService).invoke(any(), traceCaptor.capture(), any(), any(), eq(apiKey));
        assertEquals("request-id-1", traceCaptor.getValue());
    }

    @Test
    void resolveWithJwtButNoApiKeyPropagatesGatewayKeyRequirement() {
        ResourceResolveRequest req = new ResourceResolveRequest();
        req.setResourceType("agent");
        req.setResourceId("9");
        when(apiKeyScopeService.authenticateOrNull(null)).thenReturn(null);
        when(gatewayCallerResolver.resolveTrustedUserIdOrNull()).thenReturn(5L);
        when(unifiedGatewayService.resolve(any(), isNull(), eq(5L)))
                .thenThrow(new BusinessException(ResultCode.GATEWAY_API_KEY_REQUIRED, "资源解析须提供有效的 X-Api-Key"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resourceCatalogController.resolve(req, null));
        assertEquals(ResultCode.GATEWAY_API_KEY_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void invokeWithJwtButNoApiKeyPropagatesGatewayKeyRequirement() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("agent");
        request.setResourceId("9");
        request.setPayload(Map.of());
        when(apiKeyScopeService.authenticateOrNull(null)).thenReturn(null);
        when(gatewayCallerResolver.resolveTrustedUserIdOrNull()).thenReturn(42L);
        when(unifiedGatewayService.invoke(eq(42L), any(), any(), any(), isNull()))
                .thenThrow(new BusinessException(ResultCode.GATEWAY_API_KEY_REQUIRED, "统一网关调用须提供有效的 X-Api-Key"));
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resourceCatalogController.invoke(null, "rid-1", null, request, httpRequest));
        assertEquals(ResultCode.GATEWAY_API_KEY_REQUIRED.getCode(), ex.getCode());
    }
}
