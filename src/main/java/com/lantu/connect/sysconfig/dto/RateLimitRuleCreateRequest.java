package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统配置 RateLimitRuleCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class RateLimitRuleCreateRequest {

    @NotBlank
    private String name;

    /** 路径限流（HTTP Filter）遗留字段：与 target=path 等价 */
    private String pathPattern;
    private Integer limitPerMinute;
    private Integer limitPerDay;
    private Integer enabled;

    /** 网关侧 user/role/global/ip/api_key/path 等 */
    private String target;
    private String targetValue;
    private Long windowMs;
    private Integer maxRequests;
    private Integer maxTokens;
    private Integer burstLimit;
    private String action;
    private Integer priority;

    /** null / all：任意资源；否则仅对该 resource_type 的调用累计 */
    private String resourceScope;
}
