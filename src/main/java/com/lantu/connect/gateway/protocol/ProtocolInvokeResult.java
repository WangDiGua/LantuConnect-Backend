package com.lantu.connect.gateway.protocol;

public record ProtocolInvokeResult(int statusCode, String body, long latencyMs) {
}
