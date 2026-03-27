package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 系统配置 QuotaRateLimitCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class QuotaRateLimitCreateRequest {

    @NotNull
    private Long quotaId;
    @NotNull
    private String ruleKey;
    private Integer enabled;
}
