package com.lantu.connect.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResourceResolveRequest {

    @NotBlank
    private String resourceType;

    @NotBlank
    private String resourceId;

    private String version;

    /**
     * 可选扩展块，逗号分隔：observability,quality,tags
     */
    private String include;
}
