package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.SensitiveDataEncryptor;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.AuditPendingPushDebouncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ResourceRegistryRegistrationSemanticsTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PlatformRoleMapper platformRoleMapper;
    @Mock
    private ProtocolInvokerRegistry protocolInvokerRegistry;
    @Mock
    private SensitiveDataEncryptor sensitiveDataEncryptor;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private SystemNotificationFacade systemNotificationFacade;
    @Mock
    private AuditPendingPushDebouncer auditPendingPushDebouncer;
    @Mock
    private ResourceHealthService resourceHealthService;

    @InjectMocks
    private ResourceRegistryServiceImpl resourceRegistryService;

    @Test
    void validateByType_agent_defaultsMissingAgentTypeToHttpApi() {
        ResourceUpsertRequest request = new ResourceUpsertRequest();
        request.setResourceType("agent");
        request.setResourceCode("agent-default");
        request.setDisplayName("Agent Default");
        request.setRegistrationProtocol("openai_compatible");
        request.setUpstreamEndpoint("https://example.com/v1/chat/completions");
        request.setModelAlias("agent-default");
        request.setSpec(new LinkedHashMap<>());

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(resourceRegistryService, "validateByType", "agent", request));
        assertEquals("http_api", request.getAgentType());
    }

    @Test
    void validateByType_skill_forcesContextDefaults() {
        ResourceUpsertRequest request = new ResourceUpsertRequest();
        request.setResourceType("skill");
        request.setResourceCode("skill-default");
        request.setDisplayName("Skill Default");
        request.setContextPrompt("## context");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(resourceRegistryService, "validateByType", "skill", request));
        assertEquals("context_v1", request.getSkillType());
        assertEquals("context", request.getExecutionMode());
    }

    @Test
    void validateByType_mcp_rejectsStdioTransport() {
        when(protocolInvokerRegistry.isSupported("mcp")).thenReturn(true);
        ResourceUpsertRequest request = new ResourceUpsertRequest();
        request.setResourceType("mcp");
        request.setResourceCode("mcp-stdio");
        request.setDisplayName("Mcp stdio");
        request.setEndpoint("https://example.com/mcp");
        request.setProtocol("mcp");
        request.setAuthType("none");
        request.setAuthConfig(Map.of("transport", "stdio"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(resourceRegistryService, "validateByType", "mcp", request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("stdio"));
    }
}
