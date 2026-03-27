package com.lantu.connect.auth.controller;

import com.lantu.connect.auth.dto.UserInfoVO;
import com.lantu.connect.auth.service.AuthService;
import com.lantu.connect.common.result.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerWebMvcTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void shouldReturnCurrentUserInfo() throws Exception {
        when(authService.me(1L)).thenReturn(UserInfoVO.builder()
                .id("1")
                .username("admin")
                .role("platform_admin")
                .build());

        R<UserInfoVO> result = authController.me(1L);
        assertEquals(0, result.getCode());
        assertEquals("admin", result.getData().getUsername());
    }
}
