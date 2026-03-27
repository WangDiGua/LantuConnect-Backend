package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SdkGatewayControllerTest {

    @Mock
    private UnifiedGatewayService unifiedGatewayService;

    @Mock
    private ApiKeyScopeService apiKeyScopeService;

    @InjectMocks
    private SdkGatewayController sdkGatewayController;

    @Test
    void shouldRejectWhenApiKeyMissing() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("skill");
        request.setResourceId("8");
        assertThrows(BusinessException.class, () ->
                sdkGatewayController.invoke(null, null, " ", request, new MockHttpServletRequest()));
    }

    @Test
    void shouldGenerateTraceIdWhenMissing() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("skill");
        request.setResourceId("8");
        request.setPayload(Map.of("x", 1));

        ApiKey apiKey = new ApiKey();
        apiKey.setId("k1");
        when(apiKeyScopeService.authenticateOrNull("raw-key")).thenReturn(apiKey);
        when(unifiedGatewayService.invoke(any(), any(), any(), any(), any()))
                .thenReturn(InvokeResponse.builder()
                        .requestId("req-1")
                        .traceId("trace-1")
                        .resourceType("skill")
                        .resourceId("8")
                        .status("success")
                        .statusCode(200)
                        .latencyMs(9L)
                        .body("{}")
                        .build());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        R<InvokeResponse> result = sdkGatewayController.invoke(null, null, "raw-key", request, req);
        assertEquals(0, result.getCode());

        ArgumentCaptor<String> traceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(unifiedGatewayService).invoke(any(), traceIdCaptor.capture(), any(), any(), eq(apiKey));
        String traceId = traceIdCaptor.getValue();
        assertEquals(true, traceId != null && !traceId.isBlank());
    }
}
