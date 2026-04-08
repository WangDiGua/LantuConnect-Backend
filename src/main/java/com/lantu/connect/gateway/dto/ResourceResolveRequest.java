package com.lantu.connect.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "POST 资源解析请求体")
public class ResourceResolveRequest {

    @NotBlank
    @Schema(description = "agent | skill | mcp | app | dataset", example = "mcp")
    private String resourceType;

    @NotBlank
    @Schema(description = "资源主键 id（字符串形式的数字）", example = "36")
    private String resourceId;

    @Schema(description = "可选，解析版本，如 v1；不传则服务端解析默认版本")
    private String version;

    /**
     * 可选扩展块，逗号分隔：与目录 GET 相同。
     */
    @Schema(
            description = "逗号分隔：`observability`、`quality`、`tags`、`closure`、`bindings`（后两者填充 bindingClosure）。",
            example = "tags,observability,closure")
    private String include;
}
