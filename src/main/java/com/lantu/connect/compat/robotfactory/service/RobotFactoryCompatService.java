package com.lantu.connect.compat.robotfactory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryResourceContext;
import com.lantu.connect.compat.robotfactory.dto.RobotFactorySessionContext;
import com.lantu.connect.compat.robotfactory.entity.RobotFactoryProjection;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotFactoryCompatService {

    private static final ExecutorService TOOL_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "robot-factory-mcp");
        t.setDaemon(true);
        return t;
    });

    private final RobotFactoryProjectionService projectionService;
    private final RobotFactoryWhitelistService whitelistService;
    private final RobotFactorySessionService sessionService;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
    private final ObjectMapper objectMapper;
    private final RobotFactorySettingsService settingsService;

    public SseEmitter openSse(String projectionCode, HttpServletRequest request) throws IOException {
        whitelistService.requireAllowed(request);
        RobotFactoryProjection projection = projectionService.requireSyncedProjectionByCode(projectionCode);
        RobotFactoryResourceContext resource = projectionService.requirePublishedResourceForProjection(projection);
        if (!StringUtils.hasText(resource.getEndpoint())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MCP 资源未配置可调用 endpoint");
        }
        RobotFactorySessionContext session = sessionService.createSession(projection, resource);
        sessionService.sendEndpointEvent(session);
        return session.getEmitter();
    }

    public String handleMessage(String projectionCode,
                                String sessionId,
                                String body,
                                HttpServletRequest request) {
        whitelistService.requireAllowed(request);
        RobotFactorySessionContext session = sessionService.requireSession(sessionId);
        if (!projectionCode.equals(session.getProjection().getProjectionCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "session 与 projectionCode 不匹配");
        }

        Map<String, Object> rpc;
        try {
            rpc = objectMapper.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            sendJsonRpcErrorQuietly(session, null, -32700, "parse error");
            return "{\"ok\":false}";
        }

        Object id = rpc.get("id");
        String method = asText(rpc.get("method"));
        if ("initialize".equals(method) || "tools/list".equals(method)) {
            String response = invokeUpstream(session, rpc);
            sendRawMessage(session, response);
            return "{\"ok\":true}";
        }
        if ("notifications/initialized".equals(method)) {
            return "{\"ok\":true}";
        }
        if ("tools/call".equals(method)) {
            Map<String, Object> payload = enrichToolCallArguments(session, rpc);
            TOOL_EXECUTOR.submit(() -> {
                try {
                    String response = invokeUpstream(session, payload);
                    sendRawMessage(session, response);
                } catch (Exception e) {
                    log.warn("robot-factory tools/call failed: projection={} session={} msg={}",
                            projectionCode, sessionId, e.getMessage());
                    sendJsonRpcErrorQuietly(session, id, mapMcpErrorCode(e), firstNonBlank(e.getMessage(), "tool invoke failed"));
                }
            });
            return "{\"ok\":true}";
        }

        sendJsonRpcErrorQuietly(session, id, -32601, "method not found: " + method);
        return "{\"ok\":false}";
    }

    private String invokeUpstream(RobotFactorySessionContext session, Map<String, Object> rpc) {
        try {
            ProtocolInvokeResult result = protocolInvokerRegistry.invoke(
                    "mcp",
                    session.getResource().getEndpoint(),
                    Math.max(1, settingsService.getInvokeTimeoutSeconds()),
                    UUID.randomUUID().toString(),
                    rpc,
                    session.getResource().getSpec(),
                    ProtocolInvokeContext.of(
                            "robotfactory-session:" + session.getSessionId(),
                            session.getResourceId(),
                            null));
            return ensureJsonRpcPayload(rpc.get("id"), result == null ? null : result.body());
        } catch (Exception e) {
            throw e instanceof RuntimeException runtime ? runtime
                    : new BusinessException(ResultCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    private Map<String, Object> enrichToolCallArguments(RobotFactorySessionContext session, Map<String, Object> rpc) {
        Map<String, Object> copy = new LinkedHashMap<>(rpc);
        Map<String, Object> params = nestedMap(copy.get("params"));
        Map<String, Object> arguments = nestedMap(params.get("arguments"));
        if (!arguments.containsKey("_corp_id") && session.getProjection().getCorpId() != null) {
            arguments.put("_corp_id", session.getProjection().getCorpId());
        }
        String publicBaseUrl = settingsService.getPublicBaseUrl();
        if (!arguments.containsKey("_server_address") && StringUtils.hasText(publicBaseUrl)) {
            arguments.put("_server_address", publicBaseUrl.trim());
        }
        params.put("arguments", arguments);
        copy.put("params", params);
        return copy;
    }

    private void sendRawMessage(RobotFactorySessionContext session, String payload) {
        try {
            sessionService.sendMessageEvent(session, payload);
        } catch (IOException e) {
            sessionService.removeSession(session.getSessionId());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "SSE 消息发送失败");
        }
    }

    private void sendJsonRpcErrorQuietly(RobotFactorySessionContext session, Object id, int code, String message) {
        try {
            sessionService.sendMessageEvent(session, jsonRpcError(id, code, message));
        } catch (Exception ignored) {
            sessionService.removeSession(session.getSessionId());
        }
    }

    private String ensureJsonRpcPayload(Object id, String raw) {
        if (!StringUtils.hasText(raw)) {
            return jsonRpcError(id, -32000, "empty upstream response");
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isObject() && node.has("jsonrpc")) {
                return raw;
            }
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("jsonrpc", "2.0");
            wrap.put("id", id);
            wrap.put("result", objectMapper.convertValue(node, Object.class));
            return objectMapper.writeValueAsString(wrap);
        } catch (Exception e) {
            return jsonRpcError(id, -32000, abbreviate(raw));
        }
    }

    private String jsonRpcError(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("error", err);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32000,\"message\":\"serialization failed\"}}";
        }
    }

    private int mapMcpErrorCode(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            int code = businessException.getCode();
            if (code == ResultCode.FORBIDDEN.getCode()) {
                return -32009;
            }
            if (code == ResultCode.PARAM_ERROR.getCode() || code == ResultCode.NOT_FOUND.getCode()) {
                return -32602;
            }
        }
        return -32000;
    }

    private static Map<String, Object> nestedMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw).trim();
    }

    private static String abbreviate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = raw.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
