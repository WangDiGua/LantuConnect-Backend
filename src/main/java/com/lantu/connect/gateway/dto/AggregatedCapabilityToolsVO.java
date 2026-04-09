package com.lantu.connect.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@Schema(description = "绑定闭包 MCP 的 tools/list 聚合结果（OpenAI tools 形态 + 路由）")
public class AggregatedCapabilityToolsVO {

    @Schema(description = "invoke 绑定展开时写入，标明入口资源（resourceType / resourceId）")
    private Map<String, String> entry;

    @Schema(description = "OpenAI Chat Completions tools[] 形态（type=function）")
    private List<Map<String, Object>> openAiTools;

    private List<ToolDispatchRouteVO> routes;

    @Schema(description = "跳过或失败的 MCP 说明（不阻断其余条目）")
    private List<String> warnings;
}
