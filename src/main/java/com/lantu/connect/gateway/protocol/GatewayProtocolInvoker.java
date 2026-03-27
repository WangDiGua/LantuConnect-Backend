package com.lantu.connect.gateway.protocol;

import java.util.Map;

public interface GatewayProtocolInvoker {

    boolean supports(String protocol);

    /**
     * @param ctx 可空；MCP Streamable HTTP 用其缓存/携带 Mcp-Session-Id。
     */
    ProtocolInvokeResult invoke(String endpoint,
                                int timeoutSec,
                                String traceId,
                                Map<String, Object> payload,
                                Map<String, Object> spec,
                                ProtocolInvokeContext ctx) throws Exception;
}
