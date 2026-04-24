package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.config.GatewayInvokeProperties;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.protocol.McpJsonRpcProtocolInvoker;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.security.AppLaunchTokenService;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.security.GatewayGovernanceService;
import com.lantu.connect.gateway.security.GatewayUserPermissionService;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.gateway.service.GatewayBindingExpansionService;
import com.lantu.connect.gateway.service.ResourceBindingClosureService;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.trace.TraceRecorder;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedGatewayRuntimePolicyTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock TransactionTemplate transactionTemplate;
    @Mock CallLogMapper callLogMapper;
    @Mock ApiKeyScopeService apiKeyScopeService;
    @Mock GatewayUserPermissionService gatewayUserPermissionService;
    @Mock ResourceInvokeGrantService resourceInvokeGrantService;
    @Mock AppLaunchTokenService appLaunchTokenService;
    @Mock GatewayGovernanceService gatewayGovernanceService;
    @Mock ProtocolInvokerRegistry protocolInvokerRegistry;
    @Mock McpJsonRpcProtocolInvoker mcpJsonRpcProtocolInvoker;
    @Mock RuntimeAppConfigService runtimeAppConfigService;
    @Mock UserDisplayNameResolver userDisplayNameResolver;
    @Mock ResourceBindingClosureService resourceBindingClosureService;
    @Mock GatewayBindingExpansionService gatewayBindingExpansionService;
    @Mock GatewayInvokeProperties gatewayInvokeProperties;
    @Mock TraceRecorder traceRecorder;

    @Test
    void recordCircuitResultRefreshesCallabilityWhenHalfOpenMovesTowardClosed() throws Exception {
        UnifiedGatewayServiceImpl service = new UnifiedGatewayServiceImpl(
                jdbcTemplate,
                transactionTemplate,
                callLogMapper,
                new ObjectMapper(),
                apiKeyScopeService,
                gatewayUserPermissionService,
                resourceInvokeGrantService,
                appLaunchTokenService,
                gatewayGovernanceService,
                protocolInvokerRegistry,
                mcpJsonRpcProtocolInvoker,
                runtimeAppConfigService,
                userDisplayNameResolver,
                resourceBindingClosureService,
                gatewayBindingExpansionService,
                gatewayInvokeProperties,
                traceRecorder);

        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null && sql.contains("SELECT id FROM t_resource")),
                eq("agent"),
                eq("jtcsm_agent")))
                .thenReturn(java.util.List.of(Map.of("id", 70L)));
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null && sql.contains("SELECT id, current_state")),
                eq("agent"),
                eq(70L)))
                .thenReturn(java.util.List.of(Map.of(
                        "id", 700L,
                        "current_state", "HALF_OPEN",
                        "success_count", 0L,
                        "failure_count", 1L,
                        "failure_threshold", 5L,
                        "half_open_max_calls", 3L)));

        Method method = UnifiedGatewayServiceImpl.class.getDeclaredMethod(
                "recordCircuitResult", String.class, String.class, Boolean.class);
        method.setAccessible(true);
        method.invoke(service, "agent", "jtcsm_agent", Boolean.TRUE);

        verify(jdbcTemplate, atLeastOnce()).update(
                argThat(sql -> sql != null
                        && sql.contains("callability_state")
                        && sql.contains("t_resource_runtime_policy")),
                eq("agent"),
                eq(70L));
    }
}
