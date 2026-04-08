package com.lantu.connect.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "目录分页查询参数")
public class ResourceCatalogQueryRequest {

    private Integer page = 1;

    private Integer pageSize = 20;

    /**
     * agent / skill / mcp / app / dataset
     */
    private String resourceType;

    private String status;

    private String keyword;

    private String sortBy;

    private String sortOrder;


    private java.util.List<String> tags;

    /**
     * 可选扩展块，逗号分隔：observability,quality,tags
     */
    @Schema(
            description = "逗号分隔的附加块：`observability`（观测）、`quality`（质量）、`tags`（标签列表）。"
                    + "仅请求的片段会在响应中填充对应字段。",
            example = "observability,quality")
    private String include;

    /**
     * 为 true 时仅返回「当前网关可尝试 invoke」的资源（与健康探测 down/disabled、熔断 OPEN/HALF_OPEN 超限等一致，见 UnifiedGatewayServiceImpl）。
     */
    @Schema(description = "为 true 时过滤不可调用资源（与 POST /invoke 前健康/熔断校验一致）")
    private Boolean callableOnly;
}
