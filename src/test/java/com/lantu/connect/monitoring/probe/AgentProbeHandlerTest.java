package com.lantu.connect.monitoring.probe;

import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}
