package com.lantu.connect.monitoring.probe;

import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentProbeHandlerTest {

    @Test
    void probe_should_degrade_when_canary_latency_exceeds_threshold() throws Exception {
        ProtocolInvokerRegistry protocolInvokerRegistry = mock(ProtocolInvokerRegistry.class);
        when(protocolInvokerRegistry.invoke(
                eq("openai_compatible"),
                eq("https://agent.example.com/invoke"),
                anyInt(),
                anyString(),
                anyMap(),
                anyMap(),
                any()))
                .thenReturn(new ProtocolInvokeResult(200, "{\"ok\":true}", 1200L));

        AgentProbeHandler handler = new AgentProbeHandler(protocolInvokerRegistry);

        ResourceProbeTarget target = ResourceProbeTarget.builder()
                .resourceId(18L)
                .resourceType("agent")
                .resourceCode("demo-agent")
                .displayName("Demo Agent")
                .registrationProtocol("openai_compatible")
                .upstreamEndpoint("https://agent.example.com/invoke")
                .timeoutSec(15)
                .probeConfig(Map.of("latencyThresholdMs", 500))
                .canaryPayload(Map.of("query", "health check"))
                .build();

        ResourceProbeResult result = handler.probe(target);

        assertEquals("degraded", result.healthStatus());
        assertEquals("agent_provider", result.probeStrategy());
        assertEquals("agent canary latency exceeded threshold", result.failureReason());
        assertNotNull(result.evidence());
        assertEquals(1200L, result.evidence().get("latencyMs"));
    }

    @Test
    void probe_should_forward_platform_adapter_meta_and_default_payload() throws Exception {
        ProtocolInvokerRegistry protocolInvokerRegistry = mock(ProtocolInvokerRegistry.class);
        when(protocolInvokerRegistry.invoke(
                eq("openai_compatible"),
                eq("https://api.dify.ai/v1/chat-messages"),
                anyInt(),
                anyString(),
                anyMap(),
                anyMap(),
                any()))
                .thenReturn(new ProtocolInvokeResult(200, "{\"answer\":\"ok\"}", 60L));

        AgentProbeHandler handler = new AgentProbeHandler(protocolInvokerRegistry);

        ResourceProbeTarget target = ResourceProbeTarget.builder()
                .resourceId(18L)
                .resourceType("agent")
                .resourceCode("dify-agent")
                .displayName("Dify Agent")
                .registrationProtocol("openai_compatible")
                .upstreamEndpoint("https://api.dify.ai/v1/chat-messages")
                .transformProfile("dify_agent_app")
                .specExtra(Map.of(
                        "x_adapter_id", "dify",
                        "x_protocol_family", "openai_compatible"))
                .timeoutSec(15)
                .probeConfig(Map.of())
                .build();

        ResourceProbeResult result = handler.probe(target);

        assertEquals("healthy", result.healthStatus());
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> specCaptor = ArgumentCaptor.forClass(Map.class);
        verify(protocolInvokerRegistry).invoke(
                eq("openai_compatible"),
                eq("https://api.dify.ai/v1/chat-messages"),
                anyInt(),
                anyString(),
                payloadCaptor.capture(),
                specCaptor.capture(),
                any());
        assertEquals("hello", payloadCaptor.getValue().get("input"));
        assertEquals("test-session", payloadCaptor.getValue().get("session_id"));
        assertEquals("dify", specCaptor.getValue().get("x_adapter_id"));
        assertEquals("dify_agent_app", specCaptor.getValue().get("transformProfile"));
    }

    @Test
    void probe_should_merge_platform_probe_defaults_into_existing_canary_payload() throws Exception {
        ProtocolInvokerRegistry protocolInvokerRegistry = mock(ProtocolInvokerRegistry.class);
        when(protocolInvokerRegistry.invoke(
                eq("openai_compatible"),
                eq("https://api.dify.ai/v1/chat-messages"),
                anyInt(),
                anyString(),
                anyMap(),
                anyMap(),
                any()))
                .thenReturn(new ProtocolInvokeResult(200, "{\"answer\":\"ok\"}", 45L));

        AgentProbeHandler handler = new AgentProbeHandler(protocolInvokerRegistry);

        ResourceProbeTarget target = ResourceProbeTarget.builder()
                .resourceId(71L)
                .resourceType("agent")
                .resourceCode("dify-course-agent")
                .displayName("Dify Course Agent")
                .registrationProtocol("openai_compatible")
                .upstreamEndpoint("https://api.dify.ai/v1/chat-messages")
                .canaryPayload(Map.of("query", "health check"))
                .specExtra(Map.of(
                        "x_adapter_id", "dify",
                        "x_protocol_family", "openai_compatible"))
                .build();

        handler.probe(target);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(protocolInvokerRegistry).invoke(
                eq("openai_compatible"),
                eq("https://api.dify.ai/v1/chat-messages"),
                anyInt(),
                anyString(),
                payloadCaptor.capture(),
                anyMap(),
                any());
        assertEquals("health check", payloadCaptor.getValue().get("query"));
        assertEquals("test-session", payloadCaptor.getValue().get("session_id"));
        assertEquals("hello", payloadCaptor.getValue().get("input"));
    }
}
