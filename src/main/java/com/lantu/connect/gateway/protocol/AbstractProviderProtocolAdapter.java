package com.lantu.connect.gateway.protocol;

import org.springframework.util.StringUtils;

import java.util.Map;

abstract class AbstractProviderProtocolAdapter implements ProviderProtocolAdapter {

    protected String extractQuery(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        Object query = payload.get("query");
        if (StringUtils.hasText(asText(query))) {
            return asText(query).trim();
        }
        Object input = payload.get("input");
        if (StringUtils.hasText(asText(input))) {
            return asText(input).trim();
        }
        Object prompt = payload.get("prompt");
        if (StringUtils.hasText(asText(prompt))) {
            return asText(prompt).trim();
        }
        return payload.toString();
    }

    protected String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

