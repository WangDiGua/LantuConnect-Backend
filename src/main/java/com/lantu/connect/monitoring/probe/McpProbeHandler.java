package com.lantu.connect.monitoring.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.McpConnectivityProbeRequest;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class McpProbeHandler implements ResourceProbeHandler {

    private final McpConnectivityProbeService mcpConnectivityProbeService;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String resourceType) {
        return "mcp".equalsIgnoreCase(resourceType);
    }

    @Override
    public ResourceProbeResult probe(ResourceProbeTarget target) {
        if (target == null || !StringUtils.hasText(target.endpoint())) {
            return new ResourceProbeResult(
                    "down",
                    strategy(target),
                    "mcp endpoint missing",
                    "mcp endpoint missing",
                    0L,
                    null,
                    Map.of());
        }
        McpConnectivityProbeRequest initRequest = new McpConnectivityProbeRequest();
        initRequest.setEndpoint(target.endpoint());
        initRequest.setAuthType(target.authType());
        initRequest.setAuthConfig(target.authConfig());
        String transport = resolveTransport(target.protocol(), target.endpoint(), target.authConfig());
        if (StringUtils.hasText(transport)) {
            initRequest.setTransport(transport);
        }
        ProtocolInvokeContext protoCtx = ProtocolInvokeContext.of(probeSessionKey(target), target.resourceId(), null);
        McpConnectivityProbeResult initialize = mcpConnectivityProbeService.probe(initRequest, protoCtx);
        long latencyMs = Math.max(0L, initialize.getLatencyMs());
        if (!initialize.isOk()) {
            String failureReason = initialize.getMessage() == null ? "mcp initialize failed" : initialize.getMessage();
            return new ResourceProbeResult(
                    initialize.getStatusCode() == 429 ? "degraded" : "down",
                    strategy(target),
                    failureReason,
                    failureReason,
                    latencyMs,
                    initialize.getBodyPreview(),
                    Map.of("initializeStatusCode", initialize.getStatusCode()));
        }
        Map<String, Object> spec = new LinkedHashMap<>(target.authConfig() == null ? Map.of() : target.authConfig());
        if (StringUtils.hasText(target.authType())) {
            spec.put("registryAuthType", target.authType());
        }
        if (StringUtils.hasText(transport)) {
            spec.put("transport", transport);
        }
        try {
            sendInitializedNotification(target, spec, protoCtx);
            ProtocolInvokeResult toolsList = protocolInvokerRegistry.invoke(
                    "mcp",
                    target.endpoint(),
                    normalizedTimeout(target.timeoutSec(), 20),
                    "health-" + UUID.randomUUID(),
                    Map.of("method", "tools/list"),
                    spec,
                    protoCtx);
            JsonNode root = objectMapper.readTree(toolsList.body() == null ? "{}" : toolsList.body());
            JsonNode tools = root.path("result").path("tools");
            int toolCount = tools.isArray() ? tools.size() : 0;
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("initializeLatencyMs", latencyMs);
            evidence.put("toolsListStatusCode", toolsList.statusCode());
            evidence.put("toolCount", toolCount);
            if (toolsList.statusCode() >= 200 && toolsList.statusCode() < 300 && toolCount > 0) {
                return new ResourceProbeResult(
                        "healthy",
                        strategy(target),
                        "mcp initialize and tools/list succeeded",
                        null,
                        Math.max(latencyMs, toolsList.latencyMs()),
                        abbreviate(toolsList.body()),
                        evidence);
            }
            String failureReason = toolCount == 0
                    ? "mcp tools/list returned no tools"
                    : "mcp tools/list returned HTTP " + toolsList.statusCode();
            return new ResourceProbeResult(
                    "degraded",
                    strategy(target),
                    failureReason,
                    failureReason,
                    Math.max(latencyMs, toolsList.latencyMs()),
                    abbreviate(toolsList.body()),
                    evidence);
        } catch (Exception ex) {
            return new ResourceProbeResult(
                    "down",
                    strategy(target),
                    "mcp tools/list probe failed",
                    safeMessage(ex),
                    latencyMs,
                    initialize.getBodyPreview(),
                    Map.of("initializeLatencyMs", latencyMs, "exception", safeMessage(ex)));
        }
    }

    /**
     * MCP initialize 成功后补发 initialized 通知，避免部分上游在进入 steady state 前拒绝 tools/list。
     * 该通知采用 best-effort：即使上游返回 202/空体或其他非致命响应，也继续尝试 tools/list。
     */
    private void sendInitializedNotification(ResourceProbeTarget target,
                                             Map<String, Object> spec,
                                             ProtocolInvokeContext ctx) {
        try {
            protocolInvokerRegistry.invoke(
                    "mcp",
                    target.endpoint(),
                    normalizedTimeout(target.timeoutSec(), 20),
                    "health-init-" + UUID.randomUUID(),
                    Map.of("method", "notifications/initialized"),
                    spec,
                    ctx);
        } catch (Exception ignored) {
            // Some MCP servers do not require or do not explicitly acknowledge this notification.
        }
    }

    private static String strategy(ResourceProbeTarget target) {
        return target != null && "stdio".equalsIgnoreCase(target.protocol()) ? "mcp_stdio" : "mcp_jsonrpc";
    }

    private static String probeSessionKey(ResourceProbeTarget target) {
        if (target != null && target.resourceId() != null) {
            return "health-probe:mcp:" + target.resourceId();
        }
        String endpoint = target == null || target.endpoint() == null ? "unknown" : target.endpoint().trim().toLowerCase(Locale.ROOT);
        return "health-probe:mcp:" + endpoint;
    }

    private static int normalizedTimeout(Integer timeoutSec, int fallback) {
        return timeoutSec == null ? fallback : Math.max(1, Math.min(120, timeoutSec));
    }

    private static String resolveTransport(String protocol, String endpoint, Map<String, Object> authConfig) {
        if (authConfig != null) {
            Object t = authConfig.get("transport");
            if (t != null && StringUtils.hasText(String.valueOf(t))) {
                return String.valueOf(t).trim().toLowerCase(Locale.ROOT);
            }
        }
        if ("websocket".equalsIgnoreCase(protocol)) {
            return "websocket";
        }
        if (StringUtils.hasText(endpoint)) {
            String e = endpoint.trim().toLowerCase(Locale.ROOT);
            if (e.startsWith("ws://") || e.startsWith("wss://")) {
                return "websocket";
            }
        }
        return null;
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
