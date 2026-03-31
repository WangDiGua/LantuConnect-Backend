package com.lantu.connect.gateway.protocol;

import com.lantu.connect.gateway.protocol.auth.Oauth2ClientCredentialsTokenService;
import com.lantu.connect.gateway.protocol.secret.GatewaySecretRefResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * MCP 出站请求头：鉴权模式 + {@code auth_config} 密钥引用解析 + OAuth2 client_credentials。
 */
@Component
@RequiredArgsConstructor
public class McpOutboundHeaderBuilder {

    public static final String REGISTRY_AUTH_TYPE_KEY = "registryAuthType";

    private final GatewaySecretRefResolver secretRefResolver;
    private final Oauth2ClientCredentialsTokenService oauth2ClientCredentialsTokenService;

    public void applyToHttpRequest(Map<String, Object> spec, HttpRequest.Builder builder) {
        for (var e : buildHeaders(spec).entrySet()) {
            builder.header(e.getKey(), e.getValue());
        }
    }

    public void applyToWebSocket(java.net.http.WebSocket.Builder builder, Map<String, Object> spec) {
        for (var e : buildHeaders(spec).entrySet()) {
            builder.header(e.getKey(), e.getValue());
        }
    }

    public Map<String, String> buildHeaders(Map<String, Object> spec) {
        if (spec == null || spec.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> work = resolveSecretFields(new HashMap<>(spec));
        String mode = authMode(work);
        if (!StringUtils.hasText(mode) || "none".equalsIgnoreCase(mode)) {
            return Map.copyOf(customHeadersOnly(work));
        }
        LinkedHeaderMap out = new LinkedHeaderMap();
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "oauth2_client" -> {
                String tokenUrl = firstNonBlank(work, "tokenUrl", "token_url");
                String clientId = firstNonBlank(work, "clientId", "client_id");
                String clientSecret = firstNonBlank(work, "clientSecret", "client_secret");
                String scope = firstNonBlank(work, "scope");
                if (StringUtils.hasText(tokenUrl) && StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)) {
                    String tok = oauth2ClientCredentialsTokenService.getAccessToken(tokenUrl, clientId, clientSecret, scope);
                    out.put("Authorization", "Bearer " + tok.trim());
                }
            }
            case "bearer" -> {
                String t = firstNonBlank(work, "token", "bearerToken", "accessToken", "access_token");
                if (StringUtils.hasText(t)) {
                    out.put("Authorization", "Bearer " + t.trim());
                }
            }
            case "basic" -> {
                String u = str(work.get("username"));
                String p = str(work.get("password"));
                if (StringUtils.hasText(u)) {
                    String raw = u.trim() + ":" + (p == null ? "" : p);
                    String b64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                    out.put("Authorization", "Basic " + b64);
                }
            }
            case "apikey", "api_key" -> {
                String name = firstNonBlank(work, "headerName", "header_name");
                if (!StringUtils.hasText(name)) {
                    name = "X-Api-Key";
                }
                String val = firstNonBlank(work, "apiKey", "api_key", "token");
                if (StringUtils.hasText(val)) {
                    out.put(name.trim(), val.trim());
                }
            }
            default -> {
            }
        }
        out.putAll(customHeadersOnly(work));
        return out.toOrderedMap();
    }

    private Map<String, Object> resolveSecretFields(Map<String, Object> spec) {
        for (String k : new String[]{"token", "password", "clientSecret", "client_secret", "apiKey", "api_key"}) {
            Object o = spec.get(k);
            if (o instanceof Map<?, ?> m && m.get("secretRef") != null) {
                String ref = String.valueOf(m.get("secretRef")).trim();
                if (StringUtils.hasText(ref)) {
                    spec.put(k, secretRefResolver.resolveRequired(ref));
                }
            }
        }
        for (String refKey : new String[]{"tokenSecretRef", "passwordSecretRef", "clientSecretRef", "apiKeySecretRef"}) {
            if (!spec.containsKey(refKey)) {
                continue;
            }
            String ref = str(spec.get(refKey));
            if (!StringUtils.hasText(ref)) {
                continue;
            }
            String resolved = secretRefResolver.resolveRequired(ref.trim());
            if ("clientSecretRef".equals(refKey)) {
                spec.put("clientSecret", resolved);
            } else if ("apiKeySecretRef".equals(refKey)) {
                spec.put("apiKey", resolved);
            } else if ("passwordSecretRef".equals(refKey)) {
                spec.put("password", resolved);
            } else if ("tokenSecretRef".equals(refKey)) {
                spec.put("token", resolved);
            }
            spec.remove(refKey);
        }
        return spec;
    }

    private static String authMode(Map<String, Object> spec) {
        Object v = spec.get(REGISTRY_AUTH_TYPE_KEY);
        if (v != null && StringUtils.hasText(String.valueOf(v))) {
            return String.valueOf(v).trim();
        }
        v = spec.get("authType");
        if (v != null && StringUtils.hasText(String.valueOf(v))) {
            return String.valueOf(v).trim();
        }
        return null;
    }

    private static Map<String, String> customHeadersOnly(Map<String, Object> spec) {
        Object raw = spec.get("headers");
        LinkedHeaderMap out = new LinkedHeaderMap();
        if (raw instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                String k = e.getKey() == null ? null : String.valueOf(e.getKey()).trim();
                String v = e.getValue() == null ? null : String.valueOf(e.getValue());
                if (StringUtils.hasText(k) && v != null) {
                    out.put(k, v);
                }
            }
        }
        return out.toOrderedMap();
    }

    private static String firstNonBlank(Map<String, Object> spec, String... keys) {
        for (String k : keys) {
            String s = str(spec.get(k));
            if (StringUtils.hasText(s)) {
                return s.trim();
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static final class LinkedHeaderMap {
        private final LinkedHashMap<String, String> map = new LinkedHashMap<>();

        void put(String k, String v) {
            map.put(k, v);
        }

        void putAll(Map<String, String> other) {
            map.putAll(other);
        }

        Map<String, String> toOrderedMap() {
            return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(map));
        }
    }
}
