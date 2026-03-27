package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ResourceManageVO {

    private Long id;
    private String resourceType;
    private String resourceCode;
    private String displayName;
    private String description;
    private String status;
    private String sourceType;
    private Long providerId;
    private Long categoryId;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

