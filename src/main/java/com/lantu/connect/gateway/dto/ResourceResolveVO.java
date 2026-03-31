package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ResourceResolveVO {

    private String resourceType;

    private String resourceId;

    private String version;

    private String resourceCode;

    private String displayName;

    private String status;

    private String invokeType;

    private String endpoint;

    private Map<String, Object> spec;

    /**
     * 应用类资源的短时启动令牌（仅 app 类型返回）。
     */
    private String launchToken;

    /**
     * 应用类资源的后端签发启动地址（仅 app 类型返回）。
     */
    private String launchUrl;

    /**
     * include=tags 时返回。
     */
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
