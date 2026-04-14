package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;

@Component
public class OpenAiCompatibleAdapter extends AbstractProviderProtocolAdapter {
    @Override
    public String protocol() {
        return "openai_compatible";
    }

    @Override
    public ProviderProtocolRequest buildRequest(String endpoint, Map<String, Object> payload, Map<String, Object> spec, String resolvedCredential, String traceId) {
        String path = endpointPath(endpoint);
        String query = extractQuery(payload);
        String model = resolveModel(spec, "default");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        if (path.contains("/responses")) {
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
        } else {
            Object messages = payload == null ? null : payload.get("messages");
            if (messages == null) {
                messages = buildOpenAiMessages(query);
            }
            body.put("messages", messages);
            if (payload != null && payload.containsKey("stream")) {
                body.put("stream", payload.get("stream"));
            } else {
                body.put("stream", false);
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

    @Override
    public String extractText(JsonNode upstreamJson) {
        String outputText = upstreamJson.path("output_text").asText(null);
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }
        JsonNode outputNode = upstreamJson.path("output").path(0).path("content").path(0);
        String outputNodeText = outputNode.path("text").asText(null);
        if (StringUtils.hasText(outputNodeText)) {
            return outputNodeText;
        }
        JsonNode node = upstreamJson.path("choices").path(0);
        String content = node.path("message").path("content").asText(null);
        if (StringUtils.hasText(content)) {
            return content;
        }
        return node.path("text").asText("");
    }

    private String endpointPath(String endpoint) {
        try {
            return URI.create(endpoint).getPath();
        } catch (Exception ex) {
            return endpoint == null ? "" : endpoint;
        }
    }
}

