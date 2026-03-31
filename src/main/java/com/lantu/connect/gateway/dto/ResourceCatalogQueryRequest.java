package com.lantu.connect.gateway.dto;

import lombok.Data;

@Data
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

    private Long categoryId;

    private java.util.List<String> tags;

    /**
     * 可选扩展块，逗号分隔：observability,quality,tags
     */
    private String include;
}
