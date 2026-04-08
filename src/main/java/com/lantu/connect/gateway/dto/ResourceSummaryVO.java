package com.lantu.connect.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资源简要（闭包/绑定列表用）")
public class ResourceSummaryVO {

    private String resourceType;
    private String resourceId;
    private String resourceCode;
    private String displayName;
    private String status;
}
