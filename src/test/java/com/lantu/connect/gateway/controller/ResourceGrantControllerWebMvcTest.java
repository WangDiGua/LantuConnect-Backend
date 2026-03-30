package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.ResourceGrantCreateRequest;
import com.lantu.connect.gateway.dto.ResourceGrantVO;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceGrantControllerWebMvcTest {

    @Mock
    private ResourceInvokeGrantService resourceInvokeGrantService;

    @InjectMocks
    private ResourceGrantController resourceGrantController;

    @Test
    void shouldGrantByApi() {
        ResourceGrantCreateRequest request = new ResourceGrantCreateRequest();
        request.setResourceType("mcp");
        request.setResourceId(1L);
        request.setGranteeApiKeyId("k1");
        request.setActions(List.of("invoke"));
        when(resourceInvokeGrantService.grant(anyLong(), any(ResourceGrantCreateRequest.class))).thenReturn(10L);

        R<Map<String, Long>> result = resourceGrantController.grant(1L, request);
        assertEquals(0, result.getCode());
        assertEquals(10L, result.getData().get("grantId"));
    }

    @Test
    void shouldListGrants() {
        when(resourceInvokeGrantService.listByResource(anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        R<List<ResourceGrantVO>> result = resourceGrantController.list(1L, "mcp", 1L, null);
        assertEquals(0, result.getCode());
        assertEquals(0, result.getData().size());
    }

    @Test
    void shouldRevokeGrant() {
        R<Void> result = resourceGrantController.revoke(1L, 10L);
        assertEquals(0, result.getCode());
    }
}
