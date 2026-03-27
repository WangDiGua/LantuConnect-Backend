package com.lantu.connect.gateway.protocol;

import org.springframework.util.StringUtils;

/**
 * 将 MCP Streamable HTTP 返回的 SSE 文本解析为单条 JSON-RPC payload（data: 行）。
 */
public final class McpSsePayloadParser {

    private McpSsePayloadParser() {
    }

    /**
     * 优先返回 {@code id} 与 {@code jsonRpcId} 匹配的 {@code data:} 行；否则返回最后一条 JSON {@code data:}；都不存在则返回原文。
     */
    public static String extractJsonPayload(String raw, String jsonRpcId) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String matched = null;
        String lastData = null;
        for (String line : raw.split("\r?\n")) {
            String t = line.trim();
            if (!t.startsWith("data:")) {
                continue;
            }
            String json = t.substring(5).trim();
            if (!json.startsWith("{")) {
                continue;
            }
            lastData = json;
            if (StringUtils.hasText(jsonRpcId) && json.contains("\"id\":\"" + jsonRpcId + "\"")) {
                matched = json;
            }
        }
        if (matched != null) {
            return matched;
        }
        if (lastData != null) {
            return lastData;
        }
        return raw;
    }
}
