package com.lantu.connect.gateway.security;

import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.sysconfig.service.QuotaCheckService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayGovernanceServiceTest {

    @Mock
    private QuotaCheckService quotaCheckService;
    @Mock
    private PlatformRoleMapper platformRoleMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private GatewayGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new GatewayGovernanceService(quotaCheckService, platformRoleMapper, stringRedisTemplate, jdbcTemplate);
    }

    @Test
    void shouldConsumeUserQuotaWhenInvokeByUserOwnedApiKey() {
        ApiKey apiKey = new ApiKey();
        apiKey.setOwnerType("user");
        apiKey.setOwnerId("3");
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(anyString(), eq("mcp"), eq(9L))).thenReturn(Collections.emptyList());

        service.applyPreInvoke(null, apiKey, "mcp", 9L, 1);

        verify(quotaCheckService).checkAndConsume(eq(3L), anyInt(), eq("mcp"));
    }
}
