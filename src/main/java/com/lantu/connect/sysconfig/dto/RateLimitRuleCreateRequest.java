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
    private String pathPattern;
    private Integer limitPerMinute;
    private Integer limitPerDay;
    private Integer enabled;
}
