package com.lantu.connect.sandbox.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.sandbox.service.SandboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SandboxControllerTest {

    @Mock
    private SandboxService sandboxService;

    @InjectMocks
    private SandboxController sandboxController;

    @Test
    void shouldGenerateTraceIdForSandboxInvoke() {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("skill");
        request.setResourceId("8");
        when(sandboxService.sandboxInvoke(eq("st-1"), org.mockito.ArgumentMatchers.anyString(), eq("127.0.0.1"), eq(request)))
                .thenReturn(InvokeResponse.builder()
                        .requestId("req-1")
                        .traceId("trace-1")
                        .resourceType("skill")
                        .resourceId("8")
                        .status("success")
                        .statusCode(200)
                        .latencyMs(10L)
                        .body("{}")
                        .build());

        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("127.0.0.1");
        R<InvokeResponse> r = sandboxController.sandboxInvoke("st-1", null, request, http);
        assertEquals(0, r.getCode());

        ArgumentCaptor<String> traceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(sandboxService).sandboxInvoke(eq("st-1"), traceIdCaptor.capture(), eq("127.0.0.1"), eq(request));
        assertEquals(true, traceIdCaptor.getValue() != null && !traceIdCaptor.getValue().isBlank());
    }
}
