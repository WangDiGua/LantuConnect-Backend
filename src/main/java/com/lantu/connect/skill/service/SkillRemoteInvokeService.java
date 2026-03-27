package com.lantu.connect.skill.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 技能对外 HTTP 调用，带熔断（Resilience4j）。
 */
@Service
public class SkillRemoteInvokeService {

    public record InvokeHttpResult(int statusCode, String body, long latencyMs) {}

    @CircuitBreaker(name = "skillInvoke", fallbackMethod = "postJsonFallback")
    @Retry(name = "skillInvoke", fallbackMethod = "postJsonFallback")
    @Bulkhead(name = "skillInvoke", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "postJsonFallback")
    public InvokeHttpResult postJson(String url, int timeoutSec, String jsonBody) throws Exception {
        int to = Math.max(1, Math.min(timeoutSec, 120));
        long t0 = System.nanoTime();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(to))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(to))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        return new InvokeHttpResult(resp.statusCode(), resp.body(), ms);
    }

    @SuppressWarnings("unused")
    private InvokeHttpResult postJsonFallback(String url, int timeoutSec, String jsonBody, Throwable t) {
        throw new BusinessException(ResultCode.CIRCUIT_OPEN, "技能下游不可用或已熔断");
    }
}
