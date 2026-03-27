package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * MCP JSON-RPC over HTTP（Streamable HTTP / SSE 首包）或 WebSocket（ws/wss endpoint）。
 * 支持在多次调用间缓存 {@code Mcp-Session-Id}（按 API Key + endpoint，存 Redis）。
 */
@Component
@RequiredArgsConstructor
public class McpJsonRpcProtocolInvoker implements GatewayProtocolInvoker {

    private final ObjectMapper objectMapper;
    private final McpStreamSessionStore mcpStreamSessionStore;

    @Value("${lantu.integration.mcp-http-accept:application/json, text/event-stream}")
    private String mcpHttpAccept;

    @Override
    public boolean supports(String protocol) {
        return StringUtils.hasText(protocol) && "mcp".equals(protocol.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    @CircuitBreaker(name = "mcpInvoke", fallbackMethod = "invokeFallback")
    @Retry(name = "mcpInvoke", fallbackMethod = "invokeFallback")
    @Bulkhead(name = "mcpInvoke", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "invokeFallback")
    public ProtocolInvokeResult invoke(String endpoint,
                                       int timeoutSec,
                                       String traceId,
                                       Map<String, Object> payload,
                                       Map<String, Object> spec,
                                       ProtocolInvokeContext ctx) throws Exception {
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MCP endpoint 为空");
        }
        if (useWebSocket(endpoint, spec)) {
            validateWebSocketEndpoint(endpoint, spec);
            return invokeWebSocket(endpoint, timeoutSec, traceId, payload, spec);
        }
        return invokeHttpStreamable(endpoint, timeoutSec, traceId, payload, spec, ctx);
    }

    private static boolean useWebSocket(String endpoint, Map<String, Object> spec) {
        String e = endpoint.trim().toLowerCase(Locale.ROOT);
        if (e.startsWith("ws://") || e.startsWith("wss://")) {
            return true;
        }
        if (spec != null && spec.get("transport") != null) {
            return "websocket".equalsIgnoreCase(String.valueOf(spec.get("transport")).trim());
        }
        return false;
    }

    private static void validateWebSocketEndpoint(String endpoint, Map<String, Object> spec) {
        boolean wantWs = spec != null && "websocket".equalsIgnoreCase(String.valueOf(spec.get("transport")).trim());
        String e = endpoint.trim().toLowerCase(Locale.ROOT);
        if (wantWs && !(e.startsWith("ws://") || e.startsWith("wss://"))) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "MCP auth_config 中 transport=websocket 时，endpoint 须为 ws:// 或 wss:// URL");
        }
    }

    private ProtocolInvokeResult invokeHttpStreamable(String endpoint,
                                                      int timeoutSec,
                                                      String traceId,
                                                      Map<String, Object> payload,
                                                      Map<String, Object> spec,
                                                      ProtocolInvokeContext ctx) throws Exception {
        int to = Math.max(1, Math.min(120, timeoutSec));
        String bodyJson = buildJsonRpcBody(traceId, payload, spec);
        String accept = StringUtils.hasText(mcpHttpAccept) ? mcpHttpAccept.trim() : "application/json, text/event-stream";

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(to)).build();
        long t0 = System.nanoTime();
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(to))
                .header("Accept", accept)
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", traceId)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

        if (ctx != null && StringUtils.hasText(ctx.apiKeyId())) {
            mcpStreamSessionStore.getSessionId(ctx.apiKeyId(), endpoint)
                    .ifPresent(sid -> b.header("Mcp-Session-Id", sid));
        }

        HttpRequest req = b.build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);

        sessionFromResponse(resp).ifPresent(sid -> {
            if (ctx != null && StringUtils.hasText(ctx.apiKeyId())) {
                mcpStreamSessionStore.saveSessionId(ctx.apiKeyId(), endpoint, sid);
            }
        });

        String text = readHttpBody(resp, traceId);
        return new ProtocolInvokeResult(resp.statusCode(), text, ms);
    }

    private ProtocolInvokeResult invokeWebSocket(String endpoint,
                                                 int timeoutSec,
                                                 String traceId,
                                                 Map<String, Object> payload,
                                                 Map<String, Object> spec) throws Exception {
        int to = Math.max(1, Math.min(120, timeoutSec));
        String json = buildJsonRpcBody(traceId, payload, spec);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(60, to)))
                .build();

        CompletableFuture<String> textDone = new CompletableFuture<>();
        StringBuilder acc = new StringBuilder();
        long t0 = System.nanoTime();

        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(60, to)))
                .buildAsync(URI.create(endpoint), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.sendText(json, true);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        acc.append(data);
                        if (last) {
                            textDone.complete(acc.toString());
                            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                        }
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        textDone.completeExceptionally(error);
                    }
                }).get(to, TimeUnit.SECONDS);

        String respBody = textDone.get(to, TimeUnit.SECONDS);
        long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        return new ProtocolInvokeResult(200, respBody, ms);
    }

    private String buildJsonRpcBody(String traceId, Map<String, Object> payload, Map<String, Object> spec) throws Exception {
        String rpcMethod = "tools/call";
        if (payload != null && payload.get("method") != null
                && StringUtils.hasText(String.valueOf(payload.get("method")))) {
            rpcMethod = String.valueOf(payload.get("method")).trim();
        } else if (spec != null && spec.get("method") != null && StringUtils.hasText(String.valueOf(spec.get("method")))) {
            rpcMethod = String.valueOf(spec.get("method")).trim();
        }
        Map<String, Object> rpcParams = mcpRpcParams(payload);
        Map<String, Object> rpc = new LinkedHashMap<>();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", traceId);
        rpc.put("method", rpcMethod);
        rpc.put("params", rpcParams);
        return objectMapper.writeValueAsString(rpc);
    }

    private static Map<String, Object> mcpRpcParams(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Object nested = payload.get("params");
        if (nested instanceof Map<?, ?> pm) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            pm.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        LinkedHashMap<String, Object> out = new LinkedHashMap<>(payload);
        out.remove("method");
        return out;
    }

    private static Optional<String> sessionFromResponse(HttpResponse<?> resp) {
        for (var e : resp.headers().map().entrySet()) {
            if ("mcp-session-id".equalsIgnoreCase(e.getKey())) {
                return e.getValue().stream().findFirst();
            }
        }
        return Optional.empty();
    }

    private static String readHttpBody(HttpResponse<InputStream> resp, String traceId) throws IOException {
        try (InputStream in = resp.body()) {
            byte[] raw = in.readAllBytes();
            String text = new String(raw, StandardCharsets.UTF_8);
            String ct = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (ct.contains("text/event-stream") || (text.contains("event:") && text.contains("data:"))) {
                return McpSsePayloadParser.extractJsonPayload(text, traceId);
            }
            return text;
        }
    }

    @SuppressWarnings("unused")
    private ProtocolInvokeResult invokeFallback(String endpoint,
                                                int timeoutSec,
                                                String traceId,
                                                Map<String, Object> payload,
                                                Map<String, Object> spec,
                                                ProtocolInvokeContext ctx,
                                                Throwable t) {
        String msg = "{\"error\":\"MCP invoke degraded: " + t.getClass().getSimpleName() + "\"}";
        return new ProtocolInvokeResult(503, msg, 0L);
    }
}
