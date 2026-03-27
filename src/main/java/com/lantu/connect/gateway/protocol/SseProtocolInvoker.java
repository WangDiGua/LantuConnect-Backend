package com.lantu.connect.gateway.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class SseProtocolInvoker implements GatewayProtocolInvoker {

    @Override
    public boolean supports(String protocol) {
        return protocol != null && "sse".equalsIgnoreCase(protocol.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public ProtocolInvokeResult invoke(String endpoint,
                                       int timeoutSec,
                                       String traceId,
                                       Map<String, Object> payload,
                                       Map<String, Object> spec,
                                       ProtocolInvokeContext ctx) throws Exception {
        int to = Math.max(1, Math.min(120, timeoutSec));
        long t0 = System.nanoTime();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(to)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(to))
                .header("Accept", "text/event-stream")
                .header("X-Trace-Id", traceId)
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        if (resp.statusCode() >= 400) {
            log.warn("SSE invoke failed: endpoint={} status={}", endpoint, resp.statusCode());
        }
        return new ProtocolInvokeResult(resp.statusCode(), resp.body(), ms);
    }
}
