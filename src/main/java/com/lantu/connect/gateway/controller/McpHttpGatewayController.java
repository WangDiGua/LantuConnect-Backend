package com.lantu.connect.gateway.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.config.OpenApiConfiguration;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.gateway.support.GatewayCallerResolver;
import com.lantu.connect.gateway.support.GatewayInvokeResponseSupport;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.common.config.GatewayInvokeProperties;
import com.lantu.connect.usermgmt.entity.ApiKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * MCP HTTP 兼容入口：固定路径 + JSON-RPC 请求体，内部委托 {@link UnifiedGatewayService#invoke} / {@link UnifiedGatewayService#invokeStream}。
 */
@RestController
@RequestMapping("/mcp/v1/resources")
@RequiredArgsConstructor
@Tag(name = "MCP HTTP 兼容", description = "标准 MCP JSON-RPC 单路径；须 X-Api-Key；stream 见 Accept 或 stream=true")
public class McpHttpGatewayController {

    private final UnifiedGatewayService unifiedGatewayService;
    private final ApiKeyScopeService apiKeyScopeService;
    private final GatewayCallerResolver gatewayCallerResolver;
    private final RuntimeAppConfigService runtimeAppConfigService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "MCP JSON-RPC 消息", description = "与 POST /invoke 同款鉴权（API Key scope + published）；请求体为 JSON-RPC 单对象。")
    @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
    @PostMapping(value = "/{resourceType}/{resourceId}/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mcpMessage(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestParam(value = "stream", required = false) Boolean streamParam,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyRaw,
            @RequestBody(required = false) JsonNode bodyNode,
            HttpServletRequest httpRequest) throws JsonProcessingException {

        Object jsonRpcId = extractJsonRpcId(bodyNode);
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        if (apiKey == null) {
            return unauthorizedJsonRpc(jsonRpcId);
        }
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        String resolvedTraceId = StringUtils.hasText(traceId)
                ? traceId.trim()
                : (StringUtils.hasText(requestId) ? requestId.trim() : UUID.randomUUID().toString());

        Map<String, Object> payload = parsePayload(bodyNode);
        InvokeRequest invokeRequest = new InvokeRequest();
        invokeRequest.setResourceType(resourceType);
        invokeRequest.setResourceId(resourceId);
        invokeRequest.setPayload(payload);

        boolean wantStream = Boolean.TRUE.equals(streamParam)
                || (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE));
        if (wantStream) {
            StreamingResponseBody streamBody = outputStream -> unifiedGatewayService.invokeStream(
                    userId, resolvedTraceId, httpRequest.getRemoteAddr(), invokeRequest, apiKey, outputStream);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Accel-Buffering", "no")
                    .contentType(Objects.requireNonNull(MediaType.TEXT_EVENT_STREAM))
                    .body(streamBody);
        }

        try {
            InvokeResponse data = unifiedGatewayService.invoke(
                    userId, resolvedTraceId, httpRequest.getRemoteAddr(), invokeRequest, apiKey);
            String json = toJsonRpcResultBody(jsonRpcId, resolvedTraceId, data);
            int httpStatus = httpStatusForInvoke(data);
            return ResponseEntity.status(httpStatus)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .body(json);
        } catch (BusinessException e) {
            String json = objectMapper.writeValueAsString(jsonRpcErrorMap(jsonRpcId, mcpErrorCode(e), e.getMessage()));
            return ResponseEntity.ok().contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)).body(json);
        }
    }

    private int httpStatusForInvoke(InvokeResponse data) {
        GatewayInvokeProperties gw = runtimeAppConfigService.gateway();
        if (gw == null || !gw.isInvokeHttpStatusReflectsUpstream()) {
            return 200;
        }
        return GatewayInvokeResponseSupport.toHttpStatus(data).value();
    }

    private static Object extractJsonRpcId(JsonNode bodyNode) {
        if (bodyNode == null || !bodyNode.has("id") || bodyNode.get("id").isNull()) {
            return null;
        }
        JsonNode idNode = bodyNode.get("id");
        if (idNode.isTextual()) {
            return idNode.asText();
        }
        if (idNode.isIntegralNumber()) {
            return idNode.longValue();
        }
        if (idNode.isNumber()) {
            return idNode.doubleValue();
        }
        if (idNode.isBoolean()) {
            return idNode.booleanValue();
        }
        return idNode.toString();
    }

    private Map<String, Object> parsePayload(JsonNode bodyNode) {
        if (bodyNode == null || bodyNode.isNull()) {
            return Map.of();
        }
        Map<String, Object> raw = objectMapper.convertValue(bodyNode, new TypeReference<>() {
        });
        return raw != null ? raw : Map.of();
    }

    private String toJsonRpcResultBody(Object jsonRpcId, String traceId, InvokeResponse resp)
            throws JsonProcessingException {
        Object id = jsonRpcId != null ? jsonRpcId : traceId;
        boolean ok = GatewayInvokeResponseSupport.isInvokeSuccess(resp);
        String body = resp != null ? resp.getBody() : null;
        if (ok && StringUtils.hasText(body)) {
            try {
                JsonNode n = objectMapper.readTree(body);
                if (n.isObject() && n.has("jsonrpc")) {
                    return objectMapper.writeValueAsString(n);
                }
                Map<String, Object> wrap = new LinkedHashMap<>();
                wrap.put("jsonrpc", "2.0");
                wrap.put("id", id);
                wrap.put("result", objectMapper.convertValue(n, Object.class));
                return objectMapper.writeValueAsString(wrap);
            } catch (Exception ignored) {
                Map<String, Object> wrap = new LinkedHashMap<>();
                wrap.put("jsonrpc", "2.0");
                wrap.put("id", id);
                wrap.put("result", body);
                return objectMapper.writeValueAsString(wrap);
            }
        }
        String errMsg = resp != null && StringUtils.hasText(resp.getBody()) ? resp.getBody() : "invoke failed";
        return objectMapper.writeValueAsString(jsonRpcErrorMap(id, -32002, abbreviate(errMsg)));
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.strip().replace('\r', ' ').replace('\n', ' ');
        int cap = 400;
        return t.length() <= cap ? t : t.substring(0, cap) + "...";
    }

    private static Map<String, Object> jsonRpcErrorMap(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jsonrpc", "2.0");
        if (id != null) {
            out.put("id", id);
        }
        out.put("error", err);
        return out;
    }

    private ResponseEntity<String> unauthorizedJsonRpc(Object jsonRpcId) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(
                jsonRpcErrorMap(jsonRpcId, -32010, "需要有效的 X-Api-Key"));
        return ResponseEntity.status(401).contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)).body(json);
    }

    private static int mcpErrorCode(BusinessException e) {
        int c = e.getCode();
        if (c == ResultCode.UNAUTHORIZED.getCode() || c == ResultCode.GATEWAY_API_KEY_REQUIRED.getCode()) {
            return -32010;
        }
        if (c == ResultCode.FORBIDDEN.getCode()) {
            return -32009;
        }
        if (c == ResultCode.PARAM_ERROR.getCode() || c == ResultCode.NOT_FOUND.getCode()) {
            return -32602;
        }
        return -32000;
    }
}
