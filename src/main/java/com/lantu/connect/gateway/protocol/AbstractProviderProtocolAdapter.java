package com.lantu.connect.gateway.protocol;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

abstract class AbstractProviderProtocolAdapter implements ProviderProtocolAdapter {

    protected String extractQuery(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        String extracted = extractPreferredText(payload);
        if (StringUtils.hasText(extracted)) {
            return extracted.trim();
        }
        return payload.toString();
    }

    protected Map<String, Object> buildOpenAiMessagePayload(String text) {
        return Map.of("role", "user", "content", text);
    }

    protected List<Map<String, Object>> buildOpenAiMessages(String text) {
        return List.of(buildOpenAiMessagePayload(text));
    }

    protected Map<String, Object> buildResponsesMessagePayload(String text) {
        return Map.of(
                "type", "message",
                "role", "user",
                "content", List.of(Map.of(
                        "type", "input_text",
                        "text", text
                )));
    }

    protected List<Map<String, Object>> buildResponsesInput(String text) {
        return List.of(buildResponsesMessagePayload(text));
    }

    protected Map<String, Object> buildBailianCompletionInput(String text) {
        return Map.of("prompt", text);
    }

    protected List<Map<String, Object>> buildGeminiContents(String text) {
        return List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", text))
        ));
    }

    protected String resolveModel(Map<String, Object> spec, String fallback) {
        String modelAlias = asText(spec == null ? null : spec.get("modelAlias"));
        if (StringUtils.hasText(modelAlias)) {
            return modelAlias.trim();
        }
        String upstreamAgentId = asText(spec == null ? null : spec.get("upstreamAgentId"));
        if (StringUtils.hasText(upstreamAgentId)) {
            return upstreamAgentId.trim();
        }
        return fallback;
    }

    protected String resolveCustomizedModelId(Map<String, Object> spec, String fallback) {
        String customizedModelId = asText(spec == null ? null : spec.get("customizedModelId"));
        if (StringUtils.hasText(customizedModelId)) {
            return customizedModelId.trim();
        }
        customizedModelId = asText(spec == null ? null : spec.get("modelAlias"));
        if (StringUtils.hasText(customizedModelId)) {
            return customizedModelId.trim();
        }
        customizedModelId = asText(spec == null ? null : spec.get("upstreamAgentId"));
        if (StringUtils.hasText(customizedModelId)) {
            return customizedModelId.trim();
        }
        return fallback;
    }

    protected boolean isStreamRequested(Map<String, Object> payload) {
        if (payload == null) {
            return false;
        }
        Object raw = payload.get("stream");
        if (raw instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(asText(raw));
    }

    protected String extractPreferredText(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String s) {
            return s;
        }
        if (raw instanceof Map<?, ?> map) {
            String[] keys = {"query", "prompt", "input", "text", "content"};
            for (String key : keys) {
                Object value = map.get(key);
                String extracted = extractPreferredText(value);
                if (StringUtils.hasText(extracted)) {
                    return extracted;
                }
            }
            Object inputText = map.get("input_text");
            if (inputText instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
            Object messages = map.get("messages");
            String messageText = extractMessageListText(messages);
            if (StringUtils.hasText(messageText)) {
                return messageText;
            }
            Object contents = map.get("contents");
            String contentsText = extractContentListText(contents);
            if (StringUtils.hasText(contentsText)) {
                return contentsText;
            }
            Object parts = map.get("parts");
            String partsText = extractContentListText(parts);
            if (StringUtils.hasText(partsText)) {
                return partsText;
            }
            Object nestedInput = map.get("input");
            if (nestedInput != null && nestedInput != raw) {
                String nestedText = extractPreferredText(nestedInput);
                if (StringUtils.hasText(nestedText)) {
                    return nestedText;
                }
            }
            return "";
        }
        if (raw instanceof Collection<?> collection) {
            return extractCollectionText(collection);
        }
        return String.valueOf(raw);
    }

    protected String extractMessageListText(Object raw) {
        if (!(raw instanceof Collection<?> collection)) {
            return extractPreferredText(raw);
        }
        List<String> parts = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) {
                String text = extractPreferredText(item);
                if (StringUtils.hasText(text)) {
                    parts.add(text.trim());
                }
                continue;
            }
            Object content = map.get("content");
            String text = extractContentText(content);
            if (!StringUtils.hasText(text)) {
                text = extractPreferredText(map.get("text"));
            }
            if (StringUtils.hasText(text)) {
                parts.add(text.trim());
            }
        }
        return String.join("\n", parts);
    }

    protected String extractContentListText(Object raw) {
        if (!(raw instanceof Collection<?> collection)) {
            return extractPreferredText(raw);
        }
        List<String> parts = new ArrayList<>();
        for (Object item : collection) {
            String text = extractContentText(item);
            if (StringUtils.hasText(text)) {
                parts.add(text.trim());
            }
        }
        return String.join("\n", parts);
    }

    protected String extractContentText(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String s) {
            return s;
        }
        if (raw instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text instanceof String s) {
                return s;
            }
            Object value = map.get("value");
            if (value instanceof String s) {
                return s;
            }
            Object prompt = map.get("prompt");
            if (prompt instanceof String s) {
                return s;
            }
            Object inputText = map.get("input_text");
            if (inputText instanceof String s) {
                return s;
            }
            return extractPreferredText(map);
        }
        if (raw instanceof Collection<?> collection) {
            return extractCollectionText(collection);
        }
        return String.valueOf(raw);
    }

    protected String extractCollectionText(Collection<?> collection) {
        List<String> parts = new ArrayList<>();
        for (Object item : collection) {
            String text = extractPreferredText(item);
            if (StringUtils.hasText(text)) {
                parts.add(text.trim());
            }
        }
        return String.join("\n", parts);
    }

    protected String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

