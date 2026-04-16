package com.lantu.connect.monitoring.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillProbeHandlerTest {

    @Test
    void probe_should_delegate_canary_to_agent_with_health_probe_flag() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ProtocolInvokerRegistry protocolInvokerRegistry = mock(ProtocolInvokerRegistry.class);
        when(jdbcTemplate.queryForList(anyString(), eq(18L))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM t_resource_agent_ext")) {
                return List.of(Map.of(
                        "enabled", true,
                        "registration_protocol", "openai_compatible",
                        "upstream_endpoint", "https://agent.example.com/invoke",
                        "upstream_agent_id", "agent-upstream-18",
                        "credential_ref", "cred-1",
                        "transform_profile", "default",
                        "model_alias", "gpt-4.1"));
            }
            return List.of(Map.of(
                    "id", 18L,
                    "resource_type", "agent",
                    "resource_code", "delegate-agent",
                    "display_name", "Delegate Agent",
                    "status", "published"));
        });
        when(protocolInvokerRegistry.invoke(
                eq("openai_compatible"),
                eq("https://agent.example.com/invoke"),
                anyInt(),
                anyString(),
                anyMap(),
                anyMap(),
                any()))
                .thenReturn(new ProtocolInvokeResult(200, "{\"status\":\"ok\"}", 42L));

        SkillProbeHandler handler = new SkillProbeHandler(jdbcTemplate, protocolInvokerRegistry, new ObjectMapper());

        ResourceProbeTarget target = ResourceProbeTarget.builder()
                .resourceId(9L)
                .resourceType("skill")
                .resourceCode("ppt-skill")
                .displayName("PPT Skill")
                .executionMode("context")
                .contextPrompt("请生成 PPT 大纲")
                .manifest(Map.of(
                        "runtimeMode", "delegate_agent",
                        "delegate", Map.of("resourceType", "agent", "resourceId", "18")))
                .canaryPayload(Map.of("topic", "季度复盘"))
                .timeoutSec(20)
                .build();

        ResourceProbeResult result = handler.probe(target);

        assertEquals("healthy", result.healthStatus());
        assertEquals("skill_canary", result.probeStrategy());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(protocolInvokerRegistry).invoke(
                eq("openai_compatible"),
                eq("https://agent.example.com/invoke"),
                anyInt(),
                anyString(),
                payloadCaptor.capture(),
                anyMap(),
                any());
        assertTrue(String.valueOf(payloadCaptor.getValue()).contains("healthProbe=true"));
    }

    @Test
    void probe_should_degrade_prompt_bundle_when_required_canary_input_is_missing() {
        SkillProbeHandler handler = new SkillProbeHandler(mock(JdbcTemplate.class), mock(ProtocolInvokerRegistry.class), new ObjectMapper());

        ResourceProbeTarget target = ResourceProbeTarget.builder()
                .resourceId(11L)
                .resourceType("skill")
                .resourceCode("prompt-skill")
                .displayName("Prompt Skill")
                .executionMode("context")
                .contextPrompt("请总结输入")
                .parametersSchema(Map.of(
                        "type", "object",
                        "required", List.of("topic"),
                        "properties", Map.of("topic", Map.of("type", "string"))))
                .canaryPayload(Map.of())
                .build();

        ResourceProbeResult result = handler.probe(target);

        assertEquals("degraded", result.healthStatus());
        assertEquals("skill canary payload does not satisfy parameters schema", result.failureReason());
    }
}
