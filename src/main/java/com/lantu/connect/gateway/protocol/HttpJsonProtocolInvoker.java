package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class HttpJsonProtocolInvoker implements GatewayProtocolInvoker {

    private static final Set<String> SUPPORTED = Set.of("http", "rest", "openapi", "webhook");

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String protocol) {
        if (protocol == null) {
            return false;
        }
        return SUPPORTED.contains(protocol.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    @CircuitBreaker(name = "httpInvoke", fallbackMethod = "invokeFallback")
    @Retry(name = "httpInvoke", fallbackMethod = "invokeFallback")
    @Bulkhead(name = "httpInvoke", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "invokeFallback")
    public ProtocolInvokeResult invoke(String endpoint,
                                       int timeoutSec,
                                       String traceId,
                                       Map<String, Object> payload,
                                       Map<String, Object> spec,
                                       ProtocolInvokeContext ctx) throws Exception {
        int to = Math.max(1, Math.min(120, timeoutSec));
        String body = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(to)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(to))
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", traceId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        return new ProtocolInvokeResult(resp.statusCode(), resp.body(), ms);
    }

    @SuppressWarnings("unused")
    private ProtocolInvokeResult invokeFallback(String endpoint,
                                                int timeoutSec,
                                                String traceId,
                                                Map<String, Object> payload,
                                                Map<String, Object> spec,
                                                ProtocolInvokeContext ctx,
                                                Throwable t) {
        String msg = ProtocolInvokeDegradedBody.buildJson(objectMapper, "HTTP invoke degraded", t);
        return new ProtocolInvokeResult(503, msg, 0L);
    }
}
