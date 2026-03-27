package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ResourceCatalogItemVO {

    private String resourceType;

    private String resourceId;

    private String resourceCode;

    private String displayName;

    private String description;

    private String status;

    private String sourceType;

    private LocalDateTime updateTime;
}
