package com.lantu.connect.usersettings.controller;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.web.ClientIpResolver;
import com.lantu.connect.gateway.dto.ResourceGrantVO;
import com.lantu.connect.usermgmt.dto.ApiKeyDetailResponse;
import com.lantu.connect.usersettings.dto.InvokeEligibilityRequest;
import com.lantu.connect.usersettings.dto.InvokeEligibilityResponse;
import com.lantu.connect.usersettings.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsControllerWebMvcTest {

    @Mock
    private UserSettingsService userSettingsService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @InjectMocks
    private UserSettingsController userSettingsController;

    @Test
    void listApiKeyResourceGrants_defaultsResourceTypeToMcp() {
        when(userSettingsService.listResourceGrantsForApiKey(7L, "key-1", "mcp")).thenReturn(List.of());

        R<List<ResourceGrantVO>> r = userSettingsController.listApiKeyResourceGrants(7L, "key-1", null);

        assertEquals(0, r.getCode());
        assertEquals(0, r.getData().size());
        verify(userSettingsService).listResourceGrantsForApiKey(7L, "key-1", "mcp");
    }

    @Test
    void listApiKeyResourceGrants_passesExplicitResourceType() {
        when(userSettingsService.listResourceGrantsForApiKey(7L, "key-1", "skill")).thenReturn(List.of());

        R<List<ResourceGrantVO>> r = userSettingsController.listApiKeyResourceGrants(7L, "key-1", "skill");

        assertEquals(0, r.getCode());
        verify(userSettingsService).listResourceGrantsForApiKey(7L, "key-1", "skill");
    }

    @Test
    void listApiKeyResourceGrants_propagatesNotFound() {
        when(userSettingsService.listResourceGrantsForApiKey(7L, "missing", "mcp"))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "API Key 不存在"));

        assertThrows(
                BusinessException.class,
                () -> userSettingsController.listApiKeyResourceGrants(7L, "missing", null));
    }

    @Test
    void invokeEligibility_delegatesToService() {
        InvokeEligibilityRequest req = new InvokeEligibilityRequest();
        req.setResourceType("mcp");
        req.setResourceIds(List.of("58"));
        InvokeEligibilityResponse body = InvokeEligibilityResponse.builder()
                .byResourceId(Map.of("58", true))
                .build();
        when(userSettingsService.invokeEligibilityForApiKey(7L, "key-1", req)).thenReturn(body);

        R<InvokeEligibilityResponse> r = userSettingsController.invokeEligibility(7L, "key-1", req);

        assertEquals(0, r.getCode());
        assertEquals(true, r.getData().getByResourceId().get("58"));
        verify(userSettingsService).invokeEligibilityForApiKey(7L, "key-1", req);
    }

    @Test
    void getApiKeyDetail_delegatesToService() {
        ApiKeyDetailResponse body = ApiKeyDetailResponse.builder()
                .id("key-1")
                .secretPlain("sk_example")
                .build();
        when(userSettingsService.getApiKeyDetail(7L, "key-1")).thenReturn(body);

        R<ApiKeyDetailResponse> r = userSettingsController.getApiKeyDetail(7L, "key-1");

        assertEquals(0, r.getCode());
        assertEquals("sk_example", r.getData().getSecretPlain());
        verify(userSettingsService).getApiKeyDetail(7L, "key-1");
    }

    @Test
    void rotateEndpointIsRemovedFromController() {
        boolean hasRotateEndpoint = Arrays.stream(UserSettingsController.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().toLowerCase().contains("rotate"));

        assertFalse(hasRotateEndpoint);
    }
}
