package com.lantu.connect.monitoring.probe;

import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.protocol.AgentPlatformAdapterSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class AgentProbeHandler implements ResourceProbeHandler {

    private final ProtocolInvokerRegistry protocolInvokerRegistry;

    @Override
    public boolean supports(String resourceType) {
        return "agent".equalsIgnoreCase(resourceType);
    }

    @Override
    public ResourceProbeResult probe(ResourceProbeTarget target) {
        if (target == null || !StringUtils.hasText(target.upstreamEndpoint())) {
            return new ResourceProbeResult(
                    "down",
                    "agent_provider",
                    "agent upstream endpoint missing",
                    "agent upstream endpoint missing",
                    0L,
                    null,
                    Map.of());
        }
        String protocol = StringUtils.hasText(target.registrationProtocol())
                ? target.registrationProtocol().trim().toLowerCase()
                : "openai_compatible";
        Map<String, Object> spec = new LinkedHashMap<>(target.specExtra() == null ? Map.of() : target.specExtra());
        spec.put("registrationProtocol", protocol);
        spec.put("upstreamAgentId", target.upstreamAgentId());
        spec.put("credentialRef", target.credentialRef());
        spec.put("transformProfile", target.transformProfile());
        spec.put("modelAlias", target.modelAlias());
        Map<String, Object> suggestedPayload = AgentPlatformAdapterSupport.suggestedPayload(
                spec,
                target.upstreamEndpoint(),
                protocol,
                target.upstreamAgentId(),
                target.transformProfile()).orElse(Map.of());
        Map<String, Object> payload = new LinkedHashMap<>(suggestedPayload);
        if (target.canaryPayload() != null && !target.canaryPayload().isEmpty()) {
            payload.putAll(target.canaryPayload());
        }
        if (payload.isEmpty()) {
            payload.put("query", "health check");
        }
        payload.putIfAbsent("_probe", true);
        try {
            ProtocolInvokeResult result = protocolInvokerRegistry.invoke(
                    protocol,
                    target.upstreamEndpoint(),
                    normalizedTimeout(target.timeoutSec(), 15),
                    "health-" + UUID.randomUUID(),
                    payload,
                    spec,
                    ProtocolInvokeContext.of(null, target.resourceId(), null));
            long latencyMs = Math.max(0L, result.latencyMs());
            long threshold = longFromConfig(target.probeConfig(), "latencyThresholdMs", Long.MAX_VALUE);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("latencyMs", latencyMs);
            evidence.put("statusCode", result.statusCode());
            if (result.statusCode() >= 200 && result.statusCode() < 300) {
                if (latencyMs > threshold) {
                    return new ResourceProbeResult(
                            "degraded",
                            "agent_provider",
                            "agent canary exceeded latency threshold",
                            "agent canary latency exceeded threshold",
                            latencyMs,
                            abbreviate(result.body()),
                            evidence);
                }
                return new ResourceProbeResult(
                        "healthy",
                        "agent_provider",
                        "agent canary succeeded",
                        null,
                        latencyMs,
                        abbreviate(result.body()),
                        evidence);
            }
            String failureReason = result.statusCode() == 429 ? "agent upstream returned 429" : "agent upstream returned HTTP " + result.statusCode();
            return new ResourceProbeResult(
                    result.statusCode() == 429 ? "degraded" : "down",
                    "agent_provider",
                    failureReason,
                    failureReason,
                    latencyMs,
                    abbreviate(result.body()),
                    evidence);
        } catch (Exception ex) {
            return new ResourceProbeResult(
                    "down",
                    "agent_provider",
                    "agent canary invocation failed",
                    safeMessage(ex),
                    0L,
                    null,
                    Map.of("exception", safeMessage(ex)));
        }
    }

    private static int normalizedTimeout(Integer timeoutSec, int fallback) {
        return timeoutSec == null ? fallback : Math.max(1, Math.min(120, timeoutSec));
    }

    private static long longFromConfig(Map<String, Object> config, String key, long fallback) {
        if (config == null) {
            return fallback;
        }
        Object raw = config.get(key);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return raw == null ? fallback : Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String abbreviate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.length() > 1024 ? raw.substring(0, 1021) + "..." : raw;
    }

    private static String safeMessage(Throwable ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return ex == null ? "unknown" : ex.getClass().getSimpleName();
        }
        return ex.getMessage();
    }
}
