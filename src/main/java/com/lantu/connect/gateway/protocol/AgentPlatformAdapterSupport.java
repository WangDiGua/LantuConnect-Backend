package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AgentPlatformAdapterSupport {

    public static final String SPEC_ADAPTER_ID = "x_adapter_id";
    public static final String SPEC_ADAPTER_KIND = "x_adapter_kind";
    public static final String SPEC_PROTOCOL_FAMILY = "x_protocol_family";
    public static final String SPEC_PROVIDER_LABEL = "x_provider_label";
    public static final String SPEC_MODEL_ALIAS = "x_model_alias";

    public static final String ADAPTER_OPENAI = "openai";
    public static final String ADAPTER_DEEPSEEK = "deepseek";
    public static final String ADAPTER_OPENROUTER = "openrouter";
    public static final String ADAPTER_OLLAMA = "ollama";
    public static final String ADAPTER_OTHER_OPENAI = "other_openai";
    public static final String ADAPTER_BAILIAN = "bailian";
    public static final String ADAPTER_ANTHROPIC = "anthropic";
    public static final String ADAPTER_GEMINI = "gemini";
    public static final String ADAPTER_BAILIAN_APP = "bailian_app";
    public static final String ADAPTER_APPBUILDER = "appbuilder";
    public static final String ADAPTER_DIFY = "dify";
    public static final String ADAPTER_OPENAI_AGENTS = "openai_agents";
    public static final String ADAPTER_TENCENT_YUANQI = "tencent_yuanqi";
    private static final int PLATFORM_AGENT_TIMEOUT_SEC = 45;
    private static final long PLATFORM_AGENT_LATENCY_THRESHOLD_MS = 45_000L;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, AdapterMeta> REGISTRY = createRegistry();

    private AgentPlatformAdapterSupport() {
    }

    public static String resolveAdapterId(Map<String, Object> spec,
                                          String upstreamEndpoint,
                                          String registrationProtocol,
                                          String upstreamAgentId,
                                          String transformProfile) {
        String explicit = firstText(
                text(spec == null ? null : spec.get(SPEC_ADAPTER_ID)),
                text(spec == null ? null : spec.get("providerPreset")),
                text(spec == null ? null : spec.get("adapterId")));
        if (isKnownAdapter(explicit)) {
            return normalize(explicit);
        }

        String profile = normalize(transformProfile);
        if (profile.contains("dify")) {
            return ADAPTER_DIFY;
        }
        if (profile.contains("appbuilder")) {
            return ADAPTER_APPBUILDER;
        }
        if (profile.contains("yuanqi") || profile.contains("qbot") || profile.contains("lke")) {
            return ADAPTER_TENCENT_YUANQI;
        }
        if (profile.contains("openai_agent") || profile.contains("assistant")) {
            return ADAPTER_OPENAI_AGENTS;
        }
        if (profile.contains("bailian") && profile.contains("app")) {
            return ADAPTER_BAILIAN_APP;
        }

        String endpoint = normalize(upstreamEndpoint);
        String normalizedUpstreamId = normalize(upstreamAgentId);
        if (endpoint.contains("api.dify.ai")
                || endpoint.contains("/v1/chat-messages")
                || endpoint.contains("/v1/completion-messages")
                || endpoint.contains("/v1/workflows/run")) {
            return ADAPTER_DIFY;
        }
        if (endpoint.contains("qianfan.baidubce.com")
                || endpoint.contains("appbuilder.baidu")
                || endpoint.contains("/v2/agent/ai_assistant/run")
                || endpoint.contains("/v2/app/conversation")) {
            return ADAPTER_APPBUILDER;
        }
        if (endpoint.contains("lke.cloud.tencent.com")
                || endpoint.contains("/qbot/chat")
                || endpoint.contains("/qbot/chat/sse")) {
            return ADAPTER_TENCENT_YUANQI;
        }
        if (endpoint.contains("api.openai.com")
                && (endpoint.contains("/v1/responses")
                || endpoint.contains("/v1/assistants")
                || endpoint.contains("/v1/threads")
                || normalizedUpstreamId.startsWith("asst_"))) {
            return ADAPTER_OPENAI_AGENTS;
        }
        if ((endpoint.contains("dashscope") || endpoint.contains("bailian"))
                && (normalizedUpstreamId.length() > 0
                || endpoint.contains("/api/v1/apps/")
                || endpoint.contains("/api/v2/apps/")
                || endpoint.contains("/apps/"))) {
            return ADAPTER_BAILIAN_APP;
        }
        if (endpoint.contains("api.deepseek.com") || endpoint.contains("deepseek")) {
            return ADAPTER_DEEPSEEK;
        }
        if (endpoint.contains("openrouter.ai")) {
            return ADAPTER_OPENROUTER;
        }
        if ((endpoint.contains("11434") || endpoint.contains("ollama"))
                && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1") || endpoint.contains("ollama"))) {
            return ADAPTER_OLLAMA;
        }
        if (endpoint.contains("dashscope") || endpoint.contains("bailian") || endpoint.contains("qwen")) {
            return ADAPTER_BAILIAN;
        }
        if (endpoint.contains("anthropic") || endpoint.contains("/v1/messages") || endpoint.contains("claude")) {
            return ADAPTER_ANTHROPIC;
        }
        if (endpoint.contains("generativelanguage.googleapis.com")
                || endpoint.contains("gemini")
                || endpoint.contains(":generatecontent")) {
            return ADAPTER_GEMINI;
        }
        if (endpoint.contains("openai.com")) {
            return ADAPTER_OPENAI;
        }

        String protocol = normalize(registrationProtocol);
        if ("bailian_compatible".equals(protocol)) {
            return normalizedUpstreamId.isEmpty() ? ADAPTER_BAILIAN : ADAPTER_BAILIAN_APP;
        }
        if ("anthropic_messages".equals(protocol)) {
            return ADAPTER_ANTHROPIC;
        }
        if ("gemini_generatecontent".equals(protocol)) {
            return ADAPTER_GEMINI;
        }
        if ("openai_compatible".equals(protocol)) {
            return ADAPTER_OTHER_OPENAI;
        }
        return null;
    }

    public static String protocolFamily(String adapterId, String fallbackProtocol) {
        AdapterMeta meta = REGISTRY.get(normalize(adapterId));
        if (meta != null && StringUtils.hasText(meta.protocolFamily)) {
            return meta.protocolFamily;
        }
        return normalizeProtocol(fallbackProtocol);
    }

    public static boolean isPlatformAdapter(String adapterId) {
        AdapterMeta meta = REGISTRY.get(normalize(adapterId));
        return meta != null && "platform_agent".equals(meta.kind);
    }

    public static String providerLabel(String adapterId) {
        AdapterMeta meta = REGISTRY.get(normalize(adapterId));
        return meta == null ? null : meta.label;
    }

    public static String defaultTransformProfile(String adapterId, String existingTransformProfile) {
        if (StringUtils.hasText(existingTransformProfile)) {
            return existingTransformProfile.trim();
        }
        AdapterMeta meta = REGISTRY.get(normalize(adapterId));
        return meta == null ? null : meta.defaultTransformProfile;
    }

    public static Map<String, Object> mergeSpecMeta(Map<String, Object> spec,
                                                    String adapterId,
                                                    String modelAlias,
                                                    String protocolFamily) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (spec != null && !spec.isEmpty()) {
            merged.putAll(spec);
        }
        AdapterMeta meta = REGISTRY.get(normalize(adapterId));
        if (meta == null) {
            return merged;
        }
        merged.put(SPEC_ADAPTER_ID, meta.adapterId);
        merged.put(SPEC_ADAPTER_KIND, meta.kind);
        merged.put(SPEC_PROTOCOL_FAMILY, protocolFamily(meta.adapterId, protocolFamily));
        merged.put(SPEC_PROVIDER_LABEL, meta.label);
        if (StringUtils.hasText(modelAlias)) {
            merged.put(SPEC_MODEL_ALIAS, modelAlias.trim());
        }
        return merged;
    }

    public static Optional<Map<String, Object>> suggestedPayload(Map<String, Object> spec,
                                                                 String upstreamEndpoint,
                                                                 String registrationProtocol,
                                                                 String upstreamAgentId,
                                                                 String transformProfile) {
        String adapterId = resolveAdapterId(spec, upstreamEndpoint, registrationProtocol, upstreamAgentId, transformProfile);
        if (!isPlatformAdapter(adapterId)) {
            return Optional.empty();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", "hello");
        payload.put("session_id", "test-session");
        return Optional.of(payload);
    }

    public static Optional<Map<String, Object>> suggestedProbeConfig(Map<String, Object> spec,
                                                                     String upstreamEndpoint,
                                                                     String registrationProtocol,
                                                                     String upstreamAgentId,
                                                                     String transformProfile) {
        String adapterId = resolveAdapterId(spec, upstreamEndpoint, registrationProtocol, upstreamAgentId, transformProfile);
        if (!isPlatformAdapter(adapterId)) {
            return Optional.empty();
        }
        return Optional.of(Map.of("latencyThresholdMs", PLATFORM_AGENT_LATENCY_THRESHOLD_MS));
    }

    public static Optional<Integer> suggestedTimeoutSec(Map<String, Object> spec,
                                                        String upstreamEndpoint,
                                                        String registrationProtocol,
                                                        String upstreamAgentId,
                                                        String transformProfile) {
        String adapterId = resolveAdapterId(spec, upstreamEndpoint, registrationProtocol, upstreamAgentId, transformProfile);
        if (!isPlatformAdapter(adapterId)) {
            return Optional.empty();
        }
        return Optional.of(PLATFORM_AGENT_TIMEOUT_SEC);
    }

    public static ProviderProtocolRequest buildPlatformRequest(String endpoint,
                                                               Map<String, Object> payload,
                                                               Map<String, Object> spec,
                                                               String resolvedCredential,
                                                               String traceId) {
        String adapterId = resolveAdapterId(
                spec,
                endpoint,
                text(spec == null ? null : spec.get("registrationProtocol")),
                text(spec == null ? null : spec.get("upstreamAgentId")),
                text(spec == null ? null : spec.get("transformProfile")));
        if (ADAPTER_DIFY.equals(adapterId)) {
            return buildDifyRequest(endpoint, payload, resolvedCredential, traceId);
        }
        if (ADAPTER_APPBUILDER.equals(adapterId)) {
            return buildAppBuilderRequest(endpoint, payload, spec, resolvedCredential, traceId);
        }
        if (ADAPTER_TENCENT_YUANQI.equals(adapterId)) {
            return buildTencentYuanqiRequest(endpoint, payload, spec, resolvedCredential, traceId);
        }
        return null;
    }

    public static String extractResponseText(Map<String, Object> spec, JsonNode upstreamJson) {
        if (upstreamJson == null || upstreamJson.isMissingNode() || upstreamJson.isNull()) {
            return null;
        }
        String adapterId = resolveAdapterId(
                spec,
                text(spec == null ? null : spec.get("upstreamEndpoint")),
                text(spec == null ? null : spec.get("registrationProtocol")),
                text(spec == null ? null : spec.get("upstreamAgentId")),
                text(spec == null ? null : spec.get("transformProfile")));
        if (ADAPTER_DIFY.equals(adapterId)) {
            return firstText(
                    textFromPath(upstreamJson, "answer"),
                    textFromPath(upstreamJson, "data.answer"),
                    textFromPath(upstreamJson, "data.outputs.answer"),
                    textFromPath(upstreamJson, "data.outputs.text"),
                    firstScalarFromNode(nodeAt(upstreamJson, "data.outputs")));
        }
        if (ADAPTER_APPBUILDER.equals(adapterId)) {
            return firstText(
                    textFromPath(upstreamJson, "answer"),
                    textFromPath(upstreamJson, "result"),
                    textFromPath(upstreamJson, "content[0].text.info"),
                    textFromPath(upstreamJson, "content[0].text"));
        }
        return null;
    }

    public static String extractResponseText(Map<String, Object> spec, String rawBody) {
        String adapterId = resolveAdapterId(
                spec,
                text(spec == null ? null : spec.get("upstreamEndpoint")),
                text(spec == null ? null : spec.get("registrationProtocol")),
                text(spec == null ? null : spec.get("upstreamAgentId")),
                text(spec == null ? null : spec.get("transformProfile")));
        if (ADAPTER_DIFY.equals(adapterId)) {
            return extractDifyText(rawBody);
        }
        if (ADAPTER_TENCENT_YUANQI.equals(adapterId)) {
            return extractTencentYuanqiText(rawBody);
        }
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        try {
            return extractResponseText(spec, OBJECT_MAPPER.readTree(rawBody));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ProviderProtocolRequest buildDifyRequest(String endpoint,
                                                            Map<String, Object> payload,
                                                            String resolvedCredential,
                                                            String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> inputs = extractInputs(payload);
        String query = extractPreferredText(payload);
        String user = firstText(
                text(payload == null ? null : payload.get("user")),
                text(payload == null ? null : payload.get("session_id")),
                text(payload == null ? null : payload.get("conversation_id")),
                traceId);
        String path = normalizeEndpointPath(endpoint);
        if (path.contains("/workflows/run")) {
            body.put("inputs", !inputs.isEmpty() ? inputs : Map.of("input", query));
            body.put("response_mode", "blocking");
            body.put("user", user);
        } else {
            body.put("inputs", inputs);
            body.put("query", query);
            body.put("response_mode", "streaming");
            body.put("user", user);
            String conversationId = text(payload == null ? null : payload.get("conversation_id"));
            if (StringUtils.hasText(conversationId)) {
                body.put("conversation_id", conversationId.trim());
            }
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Trace-Id", traceId);
        headers.put("Accept", path.contains("/workflows/run") ? "application/json" : "text/event-stream");
        if (StringUtils.hasText(resolvedCredential)) {
            headers.put("Authorization", "Bearer " + resolvedCredential.trim());
        }
        return new ProviderProtocolRequest(endpoint, headers, JsonProtocolUtils.toJson(body));
    }

    private static ProviderProtocolRequest buildAppBuilderRequest(String endpoint,
                                                                  Map<String, Object> payload,
                                                                  Map<String, Object> spec,
                                                                  String resolvedCredential,
                                                                  String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", extractPreferredText(payload));
        body.put("stream", false);
        body.put("end_user_id", firstText(
                text(payload == null ? null : payload.get("end_user_id")),
                text(payload == null ? null : payload.get("session_id")),
                traceId));
        String conversationId = firstText(
                text(payload == null ? null : payload.get("conversation_id")),
                text(payload == null ? null : payload.get("session_id")));
        if (StringUtils.hasText(conversationId)) {
            body.put("conversation_id", conversationId.trim());
        }
        Map<String, Object> inputs = extractInputs(payload);
        if (!inputs.isEmpty()) {
            body.put("inputs", inputs);
        }
        String path = normalizeEndpointPath(endpoint);
        if (path.contains("/v2/app/conversation")) {
            String appId = text(spec == null ? null : spec.get("upstreamAgentId"));
            if (StringUtils.hasText(appId)) {
                body.put("app_id", appId.trim());
            }
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Trace-Id", traceId);
        if (StringUtils.hasText(resolvedCredential)) {
            headers.put("Authorization", "Bearer " + resolvedCredential.trim());
        }
        return new ProviderProtocolRequest(endpoint, headers, JsonProtocolUtils.toJson(body));
    }

    private static ProviderProtocolRequest buildTencentYuanqiRequest(String endpoint,
                                                                     Map<String, Object> payload,
                                                                     Map<String, Object> spec,
                                                                     String resolvedCredential,
                                                                     String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        String sessionId = firstText(
                text(payload == null ? null : payload.get("session_id")),
                traceId);
        String botAppKey = firstText(
                text(spec == null ? null : spec.get("upstreamAgentId")),
                resolvedCredential);
        if (StringUtils.hasText(botAppKey)) {
            body.put("bot_app_key", botAppKey.trim());
        }
        body.put("content", extractPreferredText(payload));
        body.put("session_id", sessionId);
        body.put("visitor_biz_id", firstText(
                text(payload == null ? null : payload.get("visitor_biz_id")),
                sessionId));
        body.put("incremental", false);
        body.put("streaming_throttle", 20);
        Object customVariables = payload == null ? null : payload.get("custom_variables");
        if (customVariables instanceof Map<?, ?> map && !map.isEmpty()) {
            body.put("custom_variables", map);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        headers.put("X-Trace-Id", traceId);
        return new ProviderProtocolRequest(endpoint, headers, JsonProtocolUtils.toJson(body));
    }

    private static Map<String, AdapterMeta> createRegistry() {
        Map<String, AdapterMeta> registry = new LinkedHashMap<>();
        register(registry, ADAPTER_OPENAI, "OpenAI", "native_model", "openai_compatible", null);
        register(registry, ADAPTER_DEEPSEEK, "DeepSeek", "native_model", "openai_compatible", null);
        register(registry, ADAPTER_OPENROUTER, "OpenRouter", "native_model", "openai_compatible", null);
        register(registry, ADAPTER_OLLAMA, "Ollama", "native_model", "openai_compatible", null);
        register(registry, ADAPTER_OTHER_OPENAI, "Other OpenAI Compatible", "native_model", "openai_compatible", null);
        register(registry, ADAPTER_BAILIAN, "百炼模型", "native_model", "bailian_compatible", null);
        register(registry, ADAPTER_ANTHROPIC, "Claude / Anthropic", "native_model", "anthropic_messages", null);
        register(registry, ADAPTER_GEMINI, "Gemini", "native_model", "gemini_generatecontent", null);
        register(registry, ADAPTER_BAILIAN_APP, "百炼智能体", "platform_agent", "bailian_compatible", "bailian_agent_app");
        register(registry, ADAPTER_APPBUILDER, "百度 AppBuilder", "platform_agent", "openai_compatible", "appbuilder_agent_app");
        register(registry, ADAPTER_DIFY, "Dify", "platform_agent", "openai_compatible", "dify_agent_app");
        register(registry, ADAPTER_OPENAI_AGENTS, "OpenAI Agent Runtime", "platform_agent", "openai_compatible", "openai_agent_runtime");
        register(registry, ADAPTER_TENCENT_YUANQI, "腾讯元器", "platform_agent", "openai_compatible", "tencent_yuanqi_agent");
        return registry;
    }

    private static void register(Map<String, AdapterMeta> registry,
                                 String adapterId,
                                 String label,
                                 String kind,
                                 String protocolFamily,
                                 String defaultTransformProfile) {
        registry.put(adapterId, new AdapterMeta(adapterId, label, kind, protocolFamily, defaultTransformProfile));
    }

    private static boolean isKnownAdapter(String adapterId) {
        return REGISTRY.containsKey(normalize(adapterId));
    }

    private static String normalizeProtocol(String protocol) {
        String normalized = normalize(protocol);
        return switch (normalized) {
            case "openai_compatible", "bailian_compatible", "anthropic_messages", "gemini_generatecontent" -> normalized;
            default -> normalized;
        };
    }

    private static String normalizeEndpointPath(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "";
        }
        try {
            URI uri = URI.create(endpoint.trim());
            return normalize(uri.getPath());
        } catch (Exception ex) {
            return normalize(endpoint);
        }
    }

    private static String extractTencentYuanqiText(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        List<String> messages = new ArrayList<>();
        String[] lines = rawBody.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (!StringUtils.hasText(data) || "[DONE]".equalsIgnoreCase(data)) {
                continue;
            }
            try {
                JsonNode node = OBJECT_MAPPER.readTree(data);
                String content = firstText(
                        textFromPath(node, "payload.content"),
                        textFromPath(node, "payload.text"),
                        textFromPath(node, "content"),
                        textFromPath(node, "text"));
                if (StringUtils.hasText(content)) {
                    messages.add(content.trim());
                }
            } catch (Exception ignored) {
                if (StringUtils.hasText(data)) {
                    messages.add(data);
                }
            }
        }
        return messages.isEmpty() ? null : String.join("\n", messages);
    }

    private static String extractDifyText(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(rawBody);
            return firstText(
                    textFromPath(node, "answer"),
                    textFromPath(node, "data.answer"),
                    textFromPath(node, "data.outputs.answer"),
                    textFromPath(node, "data.outputs.text"),
                    firstScalarFromNode(nodeAt(node, "data.outputs")));
        } catch (Exception ignored) {
            // Fall through to SSE parsing below.
        }
        List<String> fragments = new ArrayList<>();
        String[] lines = rawBody.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (!StringUtils.hasText(data) || "[DONE]".equalsIgnoreCase(data)) {
                continue;
            }
            try {
                JsonNode node = OBJECT_MAPPER.readTree(data);
                String answer = rawTextFromPath(node, "answer");
                if (!StringUtils.hasText(answer)) {
                    answer = rawTextFromPath(node, "data.answer");
                }
                if (!StringUtils.hasText(answer)) {
                    answer = rawTextFromPath(node, "data.outputs.answer");
                }
                if (!StringUtils.hasText(answer)) {
                    answer = rawTextFromPath(node, "data.outputs.text");
                }
                if (answer != null) {
                    fragments.add(answer);
                }
            } catch (Exception ignored) {
                // Ignore malformed SSE fragments and keep scanning the stream.
            }
        }
        return fragments.isEmpty() ? null : String.join("", fragments);
    }

    private static String firstScalarFromNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            String text = node.asText(null);
            return StringUtils.hasText(text) ? text : null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = firstScalarFromNode(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            return null;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String text = firstScalarFromNode(entry.getValue());
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static JsonNode nodeAt(JsonNode root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            String normalized = segment.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.contains("[") && normalized.endsWith("]")) {
                int bracket = normalized.indexOf('[');
                String field = normalized.substring(0, bracket);
                if (StringUtils.hasText(field)) {
                    current = current.path(field);
                }
                int index = Integer.parseInt(normalized.substring(bracket + 1, normalized.length() - 1));
                current = current.path(index);
            } else {
                current = current.path(normalized);
            }
        }
        return current;
    }

    private static String textFromPath(JsonNode root, String path) {
        JsonNode node = nodeAt(root, path);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private static String rawTextFromPath(JsonNode root, String path) {
        JsonNode node = nodeAt(root, path);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static Map<String, Object> extractInputs(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Object explicit = payload.get("inputs");
        if (explicit instanceof Map<?, ?> map && !map.isEmpty()) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key) || isReservedPlatformField(key)) {
                continue;
            }
            inputs.put(key, entry.getValue());
        }
        return inputs;
    }

    private static boolean isReservedPlatformField(String key) {
        String normalized = normalize(key);
        return List.of(
                "input",
                "query",
                "prompt",
                "messages",
                "contents",
                "conversation_id",
                "session_id",
                "end_user_id",
                "user",
                "visitor_biz_id",
                "custom_variables",
                "stream",
                "_probe").contains(normalized);
    }

    private static String extractPreferredText(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String s) {
            return s.trim();
        }
        if (raw instanceof Map<?, ?> map) {
            String[] keys = {"query", "prompt", "input", "text", "content"};
            for (String key : keys) {
                String extracted = extractPreferredText(map.get(key));
                if (StringUtils.hasText(extracted)) {
                    return extracted.trim();
                }
            }
            String messageText = extractPreferredText(map.get("messages"));
            if (StringUtils.hasText(messageText)) {
                return messageText.trim();
            }
            String contentText = extractPreferredText(map.get("contents"));
            if (StringUtils.hasText(contentText)) {
                return contentText.trim();
            }
            String partsText = extractPreferredText(map.get("parts"));
            if (StringUtils.hasText(partsText)) {
                return partsText.trim();
            }
            return "";
        }
        if (raw instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            for (Object item : collection) {
                String extracted = extractPreferredText(item);
                if (StringUtils.hasText(extracted)) {
                    parts.add(extracted.trim());
                }
            }
            return String.join("\n", parts);
        }
        return String.valueOf(raw);
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record AdapterMeta(String adapterId,
                               String label,
                               String kind,
                               String protocolFamily,
                               String defaultTransformProfile) {
        private AdapterMeta {
            Objects.requireNonNull(adapterId, "adapterId");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(protocolFamily, "protocolFamily");
        }
    }
}
