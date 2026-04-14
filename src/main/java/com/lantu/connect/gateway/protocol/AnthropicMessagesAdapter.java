package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;

@Component
public class AnthropicMessagesAdapter extends AbstractProviderProtocolAdapter {
    @Override
    public String protocol() {
        return "anthropic_messages";
    }

    @Override
    public ProviderProtocolRequest buildRequest(String endpoint, Map<String, Object> payload, Map<String, Object> spec, String resolvedCredential, String traceId) {
        String path = endpointPath(endpoint);
        String query = extractQuery(payload);
        String model = resolveModel(spec, "claude-3-5-sonnet-latest");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", payload != null && payload.get("max_tokens") != null ? payload.get("max_tokens") : 1024);
        Object messages = payload == null ? null : payload.get("messages");
        body.put("messages", messages == null ? buildOpenAiMessages(query) : messages);
        if (payload != null && payload.containsKey("system")) {
            body.put("system", payload.get("system"));
        }
        if (payload != null && payload.containsKey("stream")) {
            body.put("stream", payload.get("stream"));
        } else if (!path.contains("/messages")) {
            body.put("stream", false);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Trace-Id", traceId);
        headers.put("anthropic-version", "2023-06-01");
        if (StringUtils.hasText(resolvedCredential)) {
            headers.put("x-api-key", resolvedCredential.trim());
        }
        return new ProviderProtocolRequest(endpoint, headers, JsonProtocolUtils.toJson(body));
    }

    @Override
    public String extractText(JsonNode upstreamJson) {
        JsonNode content = upstreamJson.path("content");
        if (content.isArray() && content.size() > 0) {
            String text = content.path(0).path("text").asText(null);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return upstreamJson.path("completion").asText("");
    }

    private String endpointPath(String endpoint) {
        try {
            return URI.create(endpoint).getPath();
        } catch (Exception ex) {
            return endpoint == null ? "" : endpoint;
        }
    }
}

