package com.lantu.connect.gateway.protocol;

/**
 * 协议调用附加上下文：供 MCP HTTP Streamable 会话、多轮请求等使用。
 */
public record ProtocolInvokeContext(String apiKeyId, Long resourceId, Long userId) {

    public static ProtocolInvokeContext of(String apiKeyId, Long resourceId, Long userId) {
        return new ProtocolInvokeContext(apiKeyId, resourceId, userId);
    }
}
