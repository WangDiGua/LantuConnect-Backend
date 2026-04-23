package com.lantu.connect.usersettings.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.web.ClientIpResolver;
import com.lantu.connect.usermgmt.dto.ApiKeyDetailResponse;
import com.lantu.connect.usersettings.dto.InvokeEligibilityRequest;
import com.lantu.connect.usersettings.dto.InvokeEligibilityResponse;
import com.lantu.connect.usersettings.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void resourceGrantEndpointIsRemovedFromController() {
        boolean hasResourceGrantEndpoint = Arrays.stream(UserSettingsController.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().toLowerCase().contains("resourcegrant"));

        assertFalse(hasResourceGrantEndpoint);
    }
}
