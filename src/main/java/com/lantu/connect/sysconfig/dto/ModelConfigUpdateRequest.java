package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 系统配置 ModelConfigUpdateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class ModelConfigUpdateRequest {

    @NotBlank
    private String id;

    private String name;

    private String provider;

    private String modelId;

    private String endpoint;

    private String apiKey;

    private Integer maxTokens;

    private BigDecimal temperature;

    private BigDecimal topP;

    private Boolean enabled;

    private Integer rateLimit;

    private BigDecimal costPerToken;

    private String description;
}
