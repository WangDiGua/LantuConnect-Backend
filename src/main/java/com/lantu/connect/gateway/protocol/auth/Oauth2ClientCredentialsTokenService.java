package com.lantu.connect.gateway.protocol.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * OAuth2 client_credentials：换票并 Redis 缓存（按 tokenUrl+clientId+scope 维度）。
 */
@Service
@RequiredArgsConstructor
public class Oauth2ClientCredentialsTokenService {

    private static final String KEY_PREFIX = "lantu:mcp:oauth2:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RuntimeAppConfigService runtimeAppConfigService;
    @Qualifier("gatewayHttpClient")
    private final HttpClient httpClient;

    /**
     * @return access_token 明文
     */
    public synchronized String getAccessToken(String tokenUrl, String clientId, String clientSecret, String scope) {
        if (!StringUtils.hasText(tokenUrl) || !StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "OAuth2 tokenUrl、clientId、clientSecret 不能为空");
        }
        String scopeNorm = StringUtils.hasText(scope) ? scope.trim() : "";
        String cacheKey = KEY_PREFIX + sha256Hex(tokenUrl.trim() + "|" + clientId.trim() + "|" + scopeNorm);

        long skewMs = 30_000L;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                JsonNode n = objectMapper.readTree(cached);
                long exp = n.path("exp").asLong(0L);
                if (exp > System.currentTimeMillis() + skewMs) {
                    return n.path("token").asText();
                }
            } catch (JsonProcessingException ignored) {
                stringRedisTemplate.delete(cacheKey);
            }
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", clientId.trim());
        form.put("client_secret", clientSecret);
        if (StringUtils.hasText(scopeNorm)) {
            form.put("scope", scopeNorm);
        }
        String body = form.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        int oauth2ConnectTimeoutSec = runtimeAppConfigService.integration().getOauth2().getConnectTimeoutSec();
        int to = Math.max(5, Math.min(60, oauth2ConnectTimeoutSec));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl.trim()))
                .timeout(Duration.ofSeconds(to))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "OAuth2 token 请求失败 HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode root = objectMapper.readTree(resp.body());
            String access = root.path("access_token").asText(null);
            if (!StringUtils.hasText(access)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "OAuth2 响应缺少 access_token");
            }
            long expiresInSec = root.path("expires_in").asLong(3600L);
            long expMs = System.currentTimeMillis() + Math.max(60L, expiresInSec) * 1000L;
            String payload = Objects.requireNonNull(objectMapper.writeValueAsString(Map.of("token", access, "exp", expMs)));
            long ttlSec = Math.max(60L, expiresInSec - 60);
            stringRedisTemplate.opsForValue().set(cacheKey, payload, Objects.requireNonNull(Duration.ofSeconds(ttlSec)));
            return access;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "OAuth2 token 请求异常: " + e.getMessage());
        }
    }

    private static String urlEncode(String v) {
        return java.net.URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }
}
