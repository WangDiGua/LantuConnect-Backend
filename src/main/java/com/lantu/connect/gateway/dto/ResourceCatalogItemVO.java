package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /** 开发者配置的消费策略；网关 Grant 短路见后续阶段。 */
    private String accessPolicy;

    private LocalDateTime updateTime;

    /** 目录标签名（t_resource_tag_rel + t_tag），与市场筛选 tags 一致 */
    private List<String> tags;

    /**
     * include=observability 时返回。
     */
    private Map<String, Object> observability;

    /**
     * include=quality 时返回。
     */
    private Map<String, Object> quality;
}
