package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.config.IntegrationProperties;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
 * MCP JSON-RPC over HTTP（Streamable HTTP / SSE）或 WebSocket（ws/wss endpoint）。
 * 支持在多次调用间缓存 {@code Mcp-Session-Id}（按 API Key + endpoint，存 Redis）。
 */
@Component
@RequiredArgsConstructor
public class McpJsonRpcProtocolInvoker implements GatewayProtocolInvoker {

    private final ObjectMapper objectMapper;
    private final McpStreamSessionStore mcpStreamSessionStore;
    private final McpOutboundHeaderBuilder mcpOutboundHeaderBuilder;
    private final RuntimeAppConfigService runtimeAppConfigService;

    private IntegrationProperties i() {
        return runtimeAppConfigService.integration();
    }

    @Override
    public boolean supports(String protocol) {
        return StringUtils.hasText(protocol) && "mcp".equals(protocol.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isWebSocketMcp(String endpoint, Map<String, Object> spec) {
        return useWebSocket(endpoint, spec);
    }

    /**
     * 将上游 MCP HTTP 响应体（含 SSE 流）原样写入 sink，不把整包读入内存。
     */
    public void streamMcpHttpResponseTo(String endpoint,
                                        int timeoutSec,
                                        String traceId,
                                        Map<String, Object> payload,
                                        Map<String, Object> spec,
                                        ProtocolInvokeContext ctx,
                                        OutputStream sink) throws IOException {
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MCP endpoint 为空");
        }
        if (useWebSocket(endpoint, spec)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "流式接口不支持 WebSocket MCP");
        }
        int to = Math.max(1, Math.min(600, timeoutSec));
        final String bodyJson;
        try {
            bodyJson = buildJsonRpcBody(traceId, payload, spec);
        } catch (JsonProcessingException e) {
            throw new IOException("MCP 请求体构造失败: " + e.getMessage(), e);
        }
        String accept = StringUtils.hasText(i().getMcpHttpAccept()) ? i().getMcpHttpAccept().trim() : "application/json, text/event-stream";

        HttpResponse<InputStream> resp;
        try {
            resp = sendWithRedirectGuard(endpoint, to, traceId, bodyJson, accept, spec, ctx, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MCP 流式请求被中断", e);
        } catch (BusinessException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("MCP 流式请求失败: " + e.getMessage(), e);
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String err;
            try (InputStream in = resp.body()) {
                err = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (resp.statusCode() == 401 && ctx != null && StringUtils.hasText(ctx.apiKeyId())
                    && McpUpstreamSessionSignals.isSessionExpiredResponseBody(err)) {
                mcpStreamSessionStore.deleteSessionId(ctx.apiKeyId(), endpoint);
                try {
                    resp = sendWithRedirectGuard(endpoint, to, traceId, bodyJson, accept, spec, ctx, false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("MCP 流式请求被中断", e);
                } catch (BusinessException e) {
                    throw e;
                } catch (java.io.IOException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw new IOException("MCP 流式请求失败: " + e.getMessage(), e);
                }
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    try (InputStream in = resp.body()) {
                        String err2 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        throw new IOException("MCP 上游 HTTP " + resp.statusCode() + ": " + err2);
                    }
                }
            } else {
                throw new IOException("MCP 上游 HTTP " + resp.statusCode() + ": " + err);
            }
        }
        sessionFromResponse(resp).ifPresent(sid -> {
            if (ctx != null && StringUtils.hasText(ctx.apiKeyId())) {
                mcpStreamSessionStore.saveSessionId(ctx.apiKeyId(), endpoint, sid);
            }
        });
        try (InputStream in = resp.body()) {
            in.transferTo(sink);
        }
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
        String accept = StringUtils.hasText(i().getMcpHttpAccept()) ? i().getMcpHttpAccept().trim() : "application/json, text/event-stream";

        long t0 = System.nanoTime();
        HttpResponse<InputStream> resp = sendWithRedirectGuard(endpoint, to, traceId, bodyJson, accept, spec, ctx, true);
        if (resp.statusCode() == 401 && ctx != null && StringUtils.hasText(ctx.apiKeyId())) {
            String errBody;
            try (InputStream in = resp.body()) {
                errBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (McpUpstreamSessionSignals.isSessionExpiredResponseBody(errBody)) {
                mcpStreamSessionStore.deleteSessionId(ctx.apiKeyId(), endpoint);
                resp = sendWithRedirectGuard(endpoint, to, traceId, bodyJson, accept, spec, ctx, false);
            } else {
                long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
                return new ProtocolInvokeResult(401, errBody, ms);
            }
        }
        long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        sessionFromResponse(resp).ifPresent(sid -> {
            if (ctx != null && StringUtils.hasText(ctx.apiKeyId())) {
                mcpStreamSessionStore.saveSessionId(ctx.apiKeyId(), endpoint, sid);
            }
        });

        String text = readHttpBody(resp, traceId);
        return new ProtocolInvokeResult(resp.statusCode(), text, ms);
    }

    private HttpRequest.Builder startHttpRequest(String endpoint,
                                                 int to,
                                                 String traceId,
                                                 String bodyJson,
                                                 String accept,
                                                 Map<String, Object> spec,
                                                 ProtocolInvokeContext ctx,
                                                 boolean attachCachedMcpSession) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(to))
                .header("Accept", accept)
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", traceId)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

        mcpOutboundHeaderBuilder.applyToHttpRequest(spec, b);

        if (attachCachedMcpSession && ctx != null && StringUtils.hasText(ctx.apiKeyId())) {
            mcpStreamSessionStore.getSessionId(ctx.apiKeyId(), endpoint)
                    .ifPresent(sid -> b.header("Mcp-Session-Id", sid));
        }
        return b;
    }

    private ProtocolInvokeResult invokeWebSocket(String endpoint,
                                                 int timeoutSec,
                                                 String traceId,
                                                 Map<String, Object> payload,
                                                 Map<String, Object> spec) throws Exception {
        int to = Math.max(1, Math.min(120, timeoutSec));
        String json = buildJsonRpcBody(traceId, payload, spec);
        validateOutboundEndpoint(URI.create(endpoint), true);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(60, to)))
                .build();

        CompletableFuture<String> textDone = new CompletableFuture<>();
        StringBuilder acc = new StringBuilder();
        long t0 = System.nanoTime();

        var wsBuilder = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(60, to)));
        mcpOutboundHeaderBuilder.applyToWebSocket(wsBuilder, spec);
        wsBuilder
                .buildAsync(URI.create(endpoint), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.sendText(json, true);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        acc.append(data);
                        if (last) {
                            if (!textDone.isDone()) {
                                textDone.complete(acc.toString());
                            }
                            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                        }
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        if (!textDone.isDone()) {
                            textDone.complete(acc.toString());
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

    private String buildJsonRpcBody(String traceId, Map<String, Object> payload, Map<String, Object> spec)
            throws JsonProcessingException {
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
        // JSON-RPC 2.0：Notification 不得带 id。严格 MCP 上游对 notifications/initialized 带 id 或 params:{} 常返回 -32602。
        boolean isNotification = rpcMethod.startsWith("notifications/");
        if (!isNotification) {
            rpc.put("id", traceId);
        }
        rpc.put("method", rpcMethod);
        // 无参 method：省略 params；固定发 "params":{} 会被部分上游判为 Invalid request parameters。
        if (!rpcParams.isEmpty()) {
            rpc.put("params", rpcParams);
        }
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
        String msg = ProtocolInvokeDegradedBody.buildJson(objectMapper, "MCP invoke degraded", t);
        return new ProtocolInvokeResult(503, msg, 0L);
    }

    private HttpResponse<InputStream> sendWithRedirectGuard(String endpoint,
                                                            int timeoutSec,
                                                            String traceId,
                                                            String bodyJson,
                                                            String accept,
                                                            Map<String, Object> spec,
                                                            ProtocolInvokeContext ctx,
                                                            boolean attachCachedMcpSession)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(timeoutSec, 120)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        URI current = URI.create(endpoint);
        int maxRedirects = Math.max(0, i().getMcpMaxRedirects());
        int followed = 0;
        while (true) {
            validateOutboundEndpoint(current, false);
            HttpRequest req = startHttpRequest(current.toString(), timeoutSec, traceId, bodyJson, accept, spec, ctx,
                    attachCachedMcpSession).build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int code = resp.statusCode();
            if (code >= 300 && code < 400) {
                if (followed >= maxRedirects) {
                    closeQuietly(resp.body());
                    throw new BusinessException(ResultCode.PARAM_ERROR, "MCP endpoint redirect exceeds max limit");
                }
                String location = resp.headers().firstValue("location").orElse(null);
                closeQuietly(resp.body());
                if (!StringUtils.hasText(location)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "MCP redirect missing Location header");
                }
                current = current.resolve(location.trim());
                followed++;
                continue;
            }
            return resp;
        }
    }

    private void validateOutboundEndpoint(URI uri, boolean websocket) {
        String scheme = uri.getScheme();
        if (!StringUtils.hasText(scheme)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MCP endpoint scheme is required");
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (websocket) {
            if (!"ws".equals(normalizedScheme) && !"wss".equals(normalizedScheme)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "MCP WebSocket endpoint must use ws/wss");
            }
            if ("ws".equals(normalizedScheme) && !i().isMcpAllowInsecureWs()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Insecure ws:// endpoint is disabled");
            }
        } else {
            if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "MCP HTTP endpoint must use http/https");
            }
            if ("http".equals(normalizedScheme) && !i().isMcpAllowHttp()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Insecure http:// endpoint is disabled");
            }
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MCP endpoint host is required");
        }
        if (!i().isMcpAllowLocalTargets()) {
            String h = host.toLowerCase(Locale.ROOT);
            if ("localhost".equals(h) || h.endsWith(".localhost")) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "MCP endpoint must not target localhost");
            }
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                for (InetAddress addr : addresses) {
                    if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                            || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "MCP endpoint must not target private/reserved addresses");
                    }
                }
            } catch (BusinessException ex) {
                throw ex;
            } catch (UnknownHostException | SecurityException ex) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Unable to resolve MCP endpoint host");
            }
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try (InputStream stream = in) {
            stream.transferTo(OutputStream.nullOutputStream());
        } catch (IOException ignored) {
        }
    }
}
