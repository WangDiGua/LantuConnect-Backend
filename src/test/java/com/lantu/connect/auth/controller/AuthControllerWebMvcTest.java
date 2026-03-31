package com.lantu.connect.auth.controller;

import com.lantu.connect.auth.dto.UserInfoVO;
import com.lantu.connect.auth.service.AuthService;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RedisAuthRateLimiter;
import com.lantu.connect.common.util.JwtUtil;
import com.lantu.connect.common.web.ClientIpResolver;
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

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private RedisAuthRateLimiter redisAuthRateLimiter;

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
