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

    @Schema(description = "实际参与 tools/list 聚合的 MCP 数量（成功进入处理循环的条目）")
    private Integer mcpQueriedCount;

    @Schema(description = "合并后的 OpenAI function 工具条数，等于 openAiTools.size()")
    private Integer toolFunctionCount;

    @Schema(description = "是否因 maxMcpsPerAggregate / maxToolsPerResponse 配置被截断")
    private Boolean aggregateTruncated;
}
