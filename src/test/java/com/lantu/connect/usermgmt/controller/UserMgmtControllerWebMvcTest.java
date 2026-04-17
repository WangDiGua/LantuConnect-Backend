package com.lantu.connect.usermgmt.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.usermgmt.dto.ApiKeyDetailResponse;
import com.lantu.connect.usermgmt.service.UserMgmtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMgmtControllerWebMvcTest {

    @Mock
    private UserMgmtService userMgmtService;

    @InjectMocks
    private UserMgmtController userMgmtController;

    @Test
    void getApiKeyDetail_delegatesToService() {
        ApiKeyDetailResponse body = ApiKeyDetailResponse.builder()
                .id("key-1")
                .secretPlain("sk_example")
                .build();
        when(userMgmtService.getApiKeyDetail("key-1")).thenReturn(body);

        R<ApiKeyDetailResponse> result = userMgmtController.getApiKeyDetail("key-1");

        assertEquals(0, result.getCode());
        assertEquals("sk_example", result.getData().getSecretPlain());
        verify(userMgmtService).getApiKeyDetail("key-1");
    }
}
