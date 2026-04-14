package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicMessagesAdapter extends AbstractProviderProtocolAdapter {
    @Override
    public String protocol() {
        return "anthropic_messages";
    }

    @Override
    public ProviderProtocolRequest buildRequest(String endpoint, Map<String, Object> payload, Map<String, Object> spec, String resolvedCredential, String traceId) {
        String query = extractQuery(payload);
        String model = resolveModel(spec, "claude-3-5-sonnet-latest");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("messages", List.of(Map.of("role", "user", "content", query)));

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
        return upstreamJson.path("content").path(0).path("text").asText("");
    }
}

