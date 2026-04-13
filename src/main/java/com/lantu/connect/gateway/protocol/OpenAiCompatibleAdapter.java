package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleAdapter extends AbstractProviderProtocolAdapter {
    @Override
    public String protocol() {
        return "openai_compatible";
    }

    @Override
    public ProviderProtocolRequest buildRequest(String endpoint, Map<String, Object> payload, Map<String, Object> spec, String resolvedCredential, String traceId) {
        String query = extractQuery(payload);
        String model = asText(spec == null ? null : spec.get("upstreamAgentId"));
        if (!StringUtils.hasText(model)) {
            model = "default";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", query)));
        body.put("stream", false);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Trace-Id", traceId);
        if (StringUtils.hasText(resolvedCredential)) {
            headers.put("Authorization", "Bearer " + resolvedCredential.trim());
        }
        return new ProviderProtocolRequest(endpoint, headers, JsonProtocolUtils.toJson(body));
    }

    @Override
    public String extractText(JsonNode upstreamJson) {
        JsonNode node = upstreamJson.path("choices").path(0);
        String content = node.path("message").path("content").asText(null);
        if (StringUtils.hasText(content)) {
            return content;
        }
        return node.path("text").asText("");
    }
}

