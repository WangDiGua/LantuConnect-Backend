package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface ProviderProtocolAdapter {

    String protocol();

    ProviderProtocolRequest buildRequest(String endpoint,
                                         Map<String, Object> payload,
                                         Map<String, Object> spec,
                                         String resolvedCredential,
                                         String traceId);

    String extractText(JsonNode upstreamJson);
}

