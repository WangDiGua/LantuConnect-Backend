package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;

@Component
public class GeminiGenerateContentAdapter extends AbstractProviderProtocolAdapter {
    @Override
    public String protocol() {
        return "gemini_generatecontent";
    }

    @Override
    public ProviderProtocolRequest buildRequest(String endpoint, Map<String, Object> payload, Map<String, Object> spec, String resolvedCredential, String traceId) {
        String path = endpointPath(endpoint);
        String query = extractQuery(payload);
        Map<String, Object> body = new LinkedHashMap<>();
        Object contents = payload == null ? null : payload.get("contents");
        body.put("contents", contents == null ? buildGeminiContents(query) : contents);
        if (payload != null && payload.containsKey("systemInstruction")) {
            body.put("systemInstruction", payload.get("systemInstruction"));
        }
        if (payload != null && payload.containsKey("generationConfig")) {
            body.put("generationConfig", payload.get("generationConfig"));
        }
        if (payload != null && payload.containsKey("stream")) {
            body.put("stream", payload.get("stream"));
        } else if (!path.contains(":streamGenerateContent")) {
            body.put("stream", false);
        }

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
        JsonNode candidates = upstreamJson.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode parts = candidates.path(0).path("content").path("parts");
            if (parts.isArray() && parts.size() > 0) {
                String text = parts.path(0).path("text").asText(null);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return upstreamJson.path("text").asText("");
    }

    private String endpointPath(String endpoint) {
        try {
            return URI.create(endpoint).getPath();
        } catch (Exception ex) {
            return endpoint == null ? "" : endpoint;
        }
    }
}

