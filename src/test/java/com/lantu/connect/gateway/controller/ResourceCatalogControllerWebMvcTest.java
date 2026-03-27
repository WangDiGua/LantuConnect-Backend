package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceCatalogControllerWebMvcTest {

    @Mock
    private UnifiedGatewayService unifiedGatewayService;

    @Mock
    private ApiKeyScopeService apiKeyScopeService;

    @InjectMocks
    private ResourceCatalogController resourceCatalogController;

    @Test
    void shouldUseRequestIdWhenTraceIdMissing() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("mcp");
        request.setResourceId("1");
        request.setPayload(Map.of("k", "v"));

        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("raw-key")).thenReturn(apiKey);
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
        R<InvokeResponse> result = resourceCatalogController.invoke(
                null, null, "request-id-1", "raw-key", request, httpRequest);
        assertEquals(0, result.getCode());
        assertEquals("req-1", result.getData().getRequestId());

        ArgumentCaptor<String> traceCaptor = ArgumentCaptor.forClass(String.class);
        verify(unifiedGatewayService).invoke(any(), traceCaptor.capture(), any(), any(), eq(apiKey));
        assertEquals("request-id-1", traceCaptor.getValue());
    }
}
