package com.lantu.connect.gateway.protocol;

import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * MCP Streamable HTTP：在多次 /invoke 之间缓存上游返回的 SessionId（需客户端持同一 API Key）。
 */
@Component
@RequiredArgsConstructor
public class McpStreamSessionStore {

    private static final String KEY_PREFIX = "lantu:mcp:http-session:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RuntimeAppConfigService runtimeAppConfigService;

    public Optional<String> getSessionId(String apiKeyId, String endpoint) {
        if (!StringUtils.hasText(apiKeyId) || !StringUtils.hasText(endpoint)) {
            return Optional.empty();
        }
        String v = stringRedisTemplate.opsForValue().get(cacheKey(apiKeyId, endpoint));
        return StringUtils.hasText(v) ? Optional.of(v) : Optional.empty();
    }

    public void saveSessionId(String apiKeyId, String endpoint, String sessionId) {
        if (!StringUtils.hasText(apiKeyId) || !StringUtils.hasText(endpoint) || !StringUtils.hasText(sessionId)) {
            return;
        }
        int sessionTtlMinutes = runtimeAppConfigService.integration().getMcpSessionTtlMinutes();
        int minutes = Math.max(5, Math.min(24 * 60, sessionTtlMinutes));
        stringRedisTemplate.opsForValue().set(cacheKey(apiKeyId, endpoint), sessionId.trim(),
                Duration.ofMinutes(minutes));
    }

    /**
     * 立刻删除已缓存的会话 id（上游已失效而本地 TTL 尚未到期时，由网关触发一次无 Session 重试）。
     */
    public void deleteSessionId(String apiKeyId, String endpoint) {
        if (!StringUtils.hasText(apiKeyId) || !StringUtils.hasText(endpoint)) {
            return;
        }
        stringRedisTemplate.delete(cacheKey(apiKeyId, endpoint));
    }

    private static String cacheKey(String apiKeyId, String endpoint) {
        return KEY_PREFIX + sha256Hex((apiKeyId + "\n" + endpoint.trim().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(java.util.Arrays.hashCode(raw));
        }
    }
}
