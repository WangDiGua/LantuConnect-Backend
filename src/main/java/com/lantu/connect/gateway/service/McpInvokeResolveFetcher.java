package com.lantu.connect.gateway.service;

import com.lantu.connect.gateway.dto.ResourceResolveVO;

/**
 * 由 {@link com.lantu.connect.gateway.service.impl.UnifiedGatewayServiceImpl} 提供，
 * 用于在绑定展开时对单个 MCP 做 invoke 语义下的 resolve。
 */
@FunctionalInterface
public interface McpInvokeResolveFetcher {

    ResourceResolveVO resolve(long mcpResourceId);
}
