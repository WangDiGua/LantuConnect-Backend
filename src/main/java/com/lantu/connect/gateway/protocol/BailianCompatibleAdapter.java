package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;

@Component
public class BailianCompatibleAdapter extends AbstractProviderProtocolAdapter {
    @Override
    public String protocol() {
        return "bailian_compatible";
    }

    @Override
    public ProviderProtocolRequest buildRequest(String endpoint, Map<String, Object> payload, Map<String, Object> spec, String resolvedCredential, String traceId) {
        String path = endpointPath(endpoint);
        String query = extractQuery(payload);
        String customizedModelId = resolveCustomizedModelId(spec, resolveModel(spec, ""));
        Map<String, Object> body = new LinkedHashMap<>();
        if (StringUtils.hasText(customizedModelId)) {
            body.put("customized_model_id", customizedModelId);
        }
        if (path.contains("/completion")) {
            Object input = payload == null ? null : payload.get("input");
            if (input instanceof String s && StringUtils.hasText(s)) {
                input = buildBailianCompletionInput(s);
            }
            if (input == null) {
                input = buildBailianCompletionInput(query);
            }
            body.put("input", input);
            Object parameters = payload == null ? null : payload.get("parameters");
            body.put("parameters", parameters instanceof Map<?, ?> ? parameters : Map.of());
            if (payload != null && payload.containsKey("stream")) {
                body.put("stream", payload.get("stream"));
            }
        } else {
            Object input = payload == null ? null : payload.get("input");
            if (input instanceof String s && StringUtils.hasText(s)) {
                input = buildResponsesInput(s);
            }
            if (input == null) {
                input = buildResponsesInput(query);
            }
            body.put("input", input);
            if (payload != null && payload.containsKey("stream")) {
                body.put("stream", payload.get("stream"));
            } else {
                body.put("stream", false);
            }
        }
        if (!body.containsKey("stream")) {
            body.put("stream", false);
        }

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
        String text = upstreamJson.path("output_text").asText(null);
        if (StringUtils.hasText(text)) {
            return text;
        }
        JsonNode output = upstreamJson.path("output");
        if (output.isArray() && output.size() > 0) {
            JsonNode first = output.path(0);
            String nested = first.path("text").asText(null);
            if (StringUtils.hasText(nested)) {
                return nested;
            }
            JsonNode content = first.path("content");
            if (content.isArray() && content.size() > 0) {
                String contentText = content.path(0).path("text").asText(null);
                if (StringUtils.hasText(contentText)) {
                    return contentText;
                }
            }
        }
        return upstreamJson.path("choices").path(0).path("message").path("content").asText("");
    }

    private String endpointPath(String endpoint) {
        try {
            return URI.create(endpoint).getPath();
        } catch (Exception ex) {
            return endpoint == null ? "" : endpoint;
        }
    }
}

