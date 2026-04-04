package com.lantu.connect.gateway.protocol;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 识别上游 MCP（如魔搭 streamable_http）返回的「会话已失效」类错误，便于丢弃本地缓存的 {@code Mcp-Session-Id} 后重试。
 */
final class McpUpstreamSessionSignals {

    private static final Pattern SESSION_EXPIRED_CODE = Pattern.compile("SessionExpired", Pattern.CASE_INSENSITIVE);

    private McpUpstreamSessionSignals() {
    }

    static boolean isSessionExpiredResponseBody(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        if (SESSION_EXPIRED_CODE.matcher(body).find()) {
            return true;
        }
        String b = body.toLowerCase(Locale.ROOT);
        return b.contains("\"code\":\"session_expired\"")
                || b.contains("sessionexpired")
                || (b.contains("session") && b.contains("is expired"));
    }
}
