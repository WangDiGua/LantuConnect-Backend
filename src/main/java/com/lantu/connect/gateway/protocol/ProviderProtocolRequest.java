package com.lantu.connect.gateway.protocol;

import java.util.Map;

public record ProviderProtocolRequest(String endpoint, Map<String, String> headers, String body) {
}

