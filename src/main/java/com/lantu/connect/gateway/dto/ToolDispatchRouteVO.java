package com.lantu.connect.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "聚合工具名与上游 MCP tools/call 的映射")
public class ToolDispatchRouteVO {

    @Schema(description = "OpenAI function name（网关内稳定前缀）")
    private String unifiedFunctionName;

    private String resourceType;

    private String resourceId;

    @Schema(description = "上游 MCP tools/list 返回的 name")
    private String upstreamToolName;
}
