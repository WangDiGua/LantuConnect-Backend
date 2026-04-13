package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiGenerateContentAdapter extends AbstractProviderProtocolAdapter {
    @Override
    public String protocol() {
        return "gemini_generatecontent";
    }

    @Override
    public ProviderProtocolRequest buildRequest(String endpoint, Map<String, Object> payload, Map<String, Object> spec, String resolvedCredential, String traceId) {
        String query = extractQuery(payload);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", query)))));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Trace-Id", traceId);
        if (StringUtils.hasText(resolvedCredential)) {
            headers.put("x-goog-api-key", resolvedCredential.trim());
        }
        return new ProviderProtocolRequest(endpoint, headers, JsonProtocolUtils.toJson(body));
    }

    @Override
    public String extractText(JsonNode upstreamJson) {
        return upstreamJson.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
    }
}

