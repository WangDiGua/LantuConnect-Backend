package com.lantu.connect.gateway.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpUpstreamSessionSignalsTest {

    @Test
    void detectsModelScopeSessionExpiredJson() {
        String body = "{\"RequestId\":\"87297893-9476-42fe-9eae-5d6ff70ae108\",\"Code\":\"SessionExpired\","
                + "\"Message\":\"session a5f022d32d754525be7e7eadcf9ef871 is expired\"}";
        assertTrue(McpUpstreamSessionSignals.isSessionExpiredResponseBody(body));
    }

    @Test
    void detectsLowercaseCode() {
        assertTrue(McpUpstreamSessionSignals.isSessionExpiredResponseBody("{\"code\":\"sessionexpired\"}"));
    }

    @Test
    void ignoresUnrelated401() {
        assertFalse(McpUpstreamSessionSignals.isSessionExpiredResponseBody("{\"Code\":\"Unauthorized\",\"Message\":\"invalid token\"}"));
    }

    @Test
    void emptyNotExpired() {
        assertFalse(McpUpstreamSessionSignals.isSessionExpiredResponseBody(null));
        assertFalse(McpUpstreamSessionSignals.isSessionExpiredResponseBody(""));
    }
}
