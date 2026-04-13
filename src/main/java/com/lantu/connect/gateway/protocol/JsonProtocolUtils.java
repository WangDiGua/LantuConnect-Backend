package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonProtocolUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonProtocolUtils() {
    }

    static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialize failed", e);
        }
    }
}

