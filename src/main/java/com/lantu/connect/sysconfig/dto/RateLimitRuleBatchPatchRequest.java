package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * POST /system-config/rate-limits/batch：对多条规则应用同一份字段更新（与单条 PUT 语义一致，字段均为可选）。
 */
@Data
public class RateLimitRuleBatchPatchRequest {

    @NotEmpty
    @Size(max = 200)
    private List<String> ids;

    private String name;
    private String pathPattern;
    private Integer limitPerMinute;
    private Integer limitPerDay;
    private Integer enabled;

    private String target;
    private String targetValue;
    private Long windowMs;
    private Integer maxRequests;
    private Integer maxTokens;
    private Integer burstLimit;
    private String action;
    private Integer priority;
    private String resourceScope;
}
