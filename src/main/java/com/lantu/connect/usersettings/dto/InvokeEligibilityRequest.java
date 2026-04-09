package com.lantu.connect.usersettings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "按资源 id 批量预判：对应类型与 id 在库中是否存在且 status=published（不替代网关 scope 与完整鉴权链）")
public class InvokeEligibilityRequest {

    @NotBlank
    @Schema(description = "资源类型，如 mcp", example = "mcp")
    private String resourceType;

    @NotEmpty
    @Size(max = 100)
    @Schema(description = "资源主键 id 列表（字符串数字），最多 100 条")
    private List<String> resourceIds;
}
