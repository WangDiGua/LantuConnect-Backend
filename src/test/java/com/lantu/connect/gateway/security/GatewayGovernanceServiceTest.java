package com.lantu.connect.gateway.security;

import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayGovernanceServiceTest {

    @Mock
    private PlatformRoleMapper platformRoleMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private GatewayGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new GatewayGovernanceService(platformRoleMapper, stringRedisTemplate, jdbcTemplate);
    }

    @Test
    void shouldApplyPreInvokeWhenRateLimitRulesEmpty() {
        when(platformRoleMapper.selectRolesByUserId(1L)).thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.emptyList());

        GatewayGovernanceService.InvokeGovernanceLease lease = service.applyPreInvoke(1L, null, "mcp", 9L, 1);

        assertNotNull(lease);
    }
}
