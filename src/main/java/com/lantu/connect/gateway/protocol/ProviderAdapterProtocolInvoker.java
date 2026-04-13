package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.util.SensitiveDataEncryptor;
import com.lantu.connect.gateway.protocol.secret.GatewaySecretRefResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProviderAdapterProtocolInvoker implements GatewayProtocolInvoker {

    private final ObjectMapper objectMapper;
    private final List<ProviderProtocolAdapter> adapters;
    private final SensitiveDataEncryptor sensitiveDataEncryptor;
    private final GatewaySecretRefResolver secretRefResolver;
    @Qualifier("gatewayHttpClient")
    private final HttpClient httpClient;

    @Override
    public boolean supports(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return false;
        }
        String p = protocol.trim().toLowerCase(Locale.ROOT);
        return adapters.stream().map(ProviderProtocolAdapter::protocol).collect(Collectors.toSet()).contains(p);
    }

    @Override
    public ProtocolInvokeResult invoke(String endpoint, int timeoutSec, String traceId, Map<String, Object> payload, Map<String, Object> spec, ProtocolInvokeContext ctx) throws Exception {
        String protocol = String.valueOf(spec == null ? null : spec.get("registrationProtocol"));
        if (!StringUtils.hasText(protocol)) {
            protocol = String.valueOf(spec == null ? null : spec.get("protocol"));
        }
        String normalized = protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
        ProviderProtocolAdapter adapter = adapters.stream()
                .filter(it -> it.protocol().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No provider adapter for protocol: " + normalized));

        String resolvedCredential = resolveCredential(spec == null ? null : spec.get("credentialRef"));
        ProviderProtocolRequest req = adapter.buildRequest(endpoint, payload, spec, resolvedCredential, traceId);
        int to = Math.max(1, Math.min(120, timeoutSec));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(req.endpoint()))
                .timeout(Duration.ofSeconds(to))
                .POST(HttpRequest.BodyPublishers.ofString(req.body()));
        req.headers().forEach(builder::header);

        long t0 = System.nanoTime();
        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        String normalizedBody = normalizeBody(adapter, resp.statusCode(), resp.body());
        return new ProtocolInvokeResult(resp.statusCode(), normalizedBody, ms);
    }

    private String normalizeBody(ProviderProtocolAdapter adapter, int statusCode, String upstreamBody) {
        try {
            JsonNode node = objectMapper.readTree(upstreamBody);
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("text", adapter.extractText(node));
            normalized.put("upstreamStatus", statusCode);
            normalized.put("upstreamBody", node);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("text", upstreamBody);
            normalized.put("upstreamStatus", statusCode);
            normalized.put("upstreamBody", upstreamBody);
            try {
                return objectMapper.writeValueAsString(normalized);
            } catch (Exception ignore) {
                return upstreamBody;
            }
        }
    }

    private String resolveCredential(Object encryptedCredentialRef) {
        if (encryptedCredentialRef == null) {
            return null;
        }
        String encrypted = String.valueOf(encryptedCredentialRef);
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        String plain;
        try {
            plain = sensitiveDataEncryptor.decrypt(encrypted);
        } catch (RuntimeException ex) {
            plain = encrypted;
        }
        if (!StringUtils.hasText(plain)) {
            return null;
        }
        String trimmed = plain.trim();
        if (trimmed.startsWith("env:") || trimmed.startsWith("prop:") || trimmed.startsWith("vault:")) {
            return secretRefResolver.resolveRequired(trimmed);
        }
        return trimmed;
    }
}
