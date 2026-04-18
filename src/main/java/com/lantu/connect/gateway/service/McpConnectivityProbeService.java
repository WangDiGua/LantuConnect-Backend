package com.lantu.connect.gateway.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.McpConnectivityProbeRequest;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.protocol.McpJsonRpcProtocolInvoker;
import com.lantu.connect.gateway.protocol.McpOutboundHeaderBuilder;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * MCP 登记前连通性探测：向用户自管的 endpoint 发送 JSON-RPC initialize，不进行资源落库。
 */
@Service
@RequiredArgsConstructor
public class McpConnectivityProbeService {

    private static final int PROBE_TIMEOUT_SEC = 20;
    private static final int BODY_PREVIEW_MAX = 800;

    private final McpJsonRpcProtocolInvoker mcpJsonRpcProtocolInvoker;

    public McpConnectivityProbeResult probe(McpConnectivityProbeRequest request) {
        return probe(request, null);
    }

    public McpConnectivityProbeResult probe(McpConnectivityProbeRequest request, ProtocolInvokeContext ctx) {
        String endpoint = request.getEndpoint() == null ? "" : request.getEndpoint().trim();
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "endpoint 不能为空");
        }
        Map<String, Object> spec = new LinkedHashMap<>();
        if (request.getAuthConfig() != null && !request.getAuthConfig().isEmpty()) {
            spec.putAll(request.getAuthConfig());
        }
        String authType = request.getAuthType() == null ? "" : request.getAuthType().trim();
        if (StringUtils.hasText(authType)) {
            spec.put(McpOutboundHeaderBuilder.REGISTRY_AUTH_TYPE_KEY, authType);
        }
        String transport = request.getTransport() == null ? "" : request.getTransport().trim().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(transport)) {
            spec.put("transport", transport);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", "initialize");
        Map<String, Object> initParams = new LinkedHashMap<>();
        initParams.put("protocolVersion", "2024-11-05");
        initParams.put("capabilities", Map.of());
        initParams.put("clientInfo", Map.of("name", "lantu-registry-probe", "version", "1.0"));
        payload.put("params", initParams);

        String traceId = "probe-" + UUID.randomUUID();
        try {
            ProtocolInvokeResult r = mcpJsonRpcProtocolInvoker.invoke(
                    endpoint, PROBE_TIMEOUT_SEC, traceId, payload, spec, ctx);
            return toResult(r);
        } catch (BusinessException ex) {
            return McpConnectivityProbeResult.builder()
                    .ok(false)
                    .statusCode(0)
                    .latencyMs(0L)
                    .message(ex.getMessage())
                    .build();
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return McpConnectivityProbeResult.builder()
                    .ok(false)
                    .statusCode(0)
                    .latencyMs(0L)
                    .message("探测失败: " + msg)
                    .build();
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return McpConnectivityProbeResult.builder()
                    .ok(false)
                    .statusCode(0)
                    .latencyMs(0L)
                    .message("探测失败: " + msg)
                    .build();
        } catch (Exception ex) {
            // GatewayProtocolInvoker.invoke declares throws Exception; cover any remaining checked types.
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return McpConnectivityProbeResult.builder()
                    .ok(false)
                    .statusCode(0)
                    .latencyMs(0L)
                    .message("探测失败: " + msg)
                    .build();
        }
    }

    private static McpConnectivityProbeResult toResult(ProtocolInvokeResult r) {
        int code = r.statusCode();
        String body = r.body() == null ? "" : r.body();
        String preview = body.length() > BODY_PREVIEW_MAX ? body.substring(0, BODY_PREVIEW_MAX) + "…" : body;
        boolean httpOk = code >= 200 && code < 300;
        boolean rpcOk = httpOk && rpcLooksSuccessful(body);
        String message;
        if (rpcOk) {
            message = "探测成功：上游返回 HTTP " + code + "，响应疑似 MCP initialize 成功。";
        } else if (httpOk) {
            message = "上游返回 HTTP " + code + "，但未看到 JSON-RPC result，请检查鉴权、路径或是否要求会话头。";
        } else {
            message = "上游返回 HTTP " + code + "，请检查地址可达性与鉴权配置。";
        }
        return McpConnectivityProbeResult.builder()
                .ok(rpcOk)
                .statusCode(code)
                .latencyMs(r.latencyMs())
                .message(message)
                .bodyPreview(preview.isEmpty() ? null : preview)
                .build();
    }

    private static boolean rpcLooksSuccessful(String body) {
        String t = body.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (t.contains("\"error\":") && t.contains("\"jsonrpc\"")) {
            return false;
        }
        if (t.contains("\"jsonrpc\"") && t.contains("\"result\"")) {
            return true;
        }
        return !t.contains("\"error\":");
    }
}
