package com.lantu.connect.gateway.security;

import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.config.GatewayInvokeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GatewayGovernanceServiceTest {

    @Mock
    private PlatformRoleMapper platformRoleMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private GatewayGovernanceService service;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        GatewayInvokeProperties properties = new GatewayInvokeProperties();
        properties.getCapabilities().setDefaultMaxConcurrentPerResource(2);
        service = new GatewayGovernanceService(platformRoleMapper, stringRedisTemplate, jdbcTemplate, properties);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> mockedValueOperations = mock(ValueOperations.class);
        valueOperations = mockedValueOperations;
        when(stringRedisTemplate.opsForValue()).thenReturn(mockedValueOperations);
    }

    @Test
    void shouldApplyPreInvokeWhenConcurrentPermitAvailable() {
        when(platformRoleMapper.selectRolesByUserId(1L)).thenReturn(Collections.<PlatformRole>emptyList());
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("t_rate_limit_rule")) {
                return Collections.emptyList();
            }
            if (sql.contains("t_resource_agent_ext")) {
                return List.of(Map.of("max_concurrency", 2));
            }
            return Collections.emptyList();
        }).when(jdbcTemplate).queryForList(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
        when(valueOperations.increment(anyString(), anyLong())).thenReturn(1L);
        when(valueOperations.decrement(anyString(), anyLong())).thenReturn(0L);

        GatewayGovernanceService.InvokeGovernanceLease lease = service.applyPreInvoke(1L, null, "agent", 9L, 1);

        assertNotNull(lease);
        assertEquals("gw:concurrent:agent:9", lease.concurrentKey());

        service.release(lease);
    }

    @Test
    void shouldRejectWhenConcurrentPermitExceeded() {
        when(platformRoleMapper.selectRolesByUserId(1L)).thenReturn(Collections.<PlatformRole>emptyList());
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("t_rate_limit_rule")) {
                return Collections.emptyList();
            }
            if (sql.contains("t_resource_agent_ext")) {
                return List.of(Map.of("max_concurrency", 1));
            }
            return Collections.emptyList();
        }).when(jdbcTemplate).queryForList(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
        when(valueOperations.increment(anyString(), anyLong())).thenReturn(2L);
        when(valueOperations.decrement(anyString(), anyLong())).thenReturn(1L);

        assertThrows(com.lantu.connect.common.exception.BusinessException.class,
                () -> service.applyPreInvoke(1L, null, "agent", 9L, 1));
    }
}
