package com.lantu.connect.sysconfig.dto;

import lombok.Data;

/**
 * 资源级限流（t_quota_rate_limit）创建请求：支持绑定配额（兼容旧字段）或直接绑定五类资源实例。
 */
@Data
public class QuotaRateLimitCreateRequest {

    /** 旧版：仅创建挂靠在配额上的占位规则 */
    private Long quotaId;
    private String ruleKey;
    private Integer enabled;

    /** 网关 enforceResourceRateLimit：target_type + target_id 对应 t_resource */
    private String name;
    private String targetType;
    private Long targetId;
    private String targetName;
    private Integer maxRequestsPerMin;
    private Integer maxRequestsPerHour;
    private Integer maxConcurrent;
}
