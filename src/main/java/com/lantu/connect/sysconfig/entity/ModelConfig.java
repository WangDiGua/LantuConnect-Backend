package com.lantu.connect.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型配置实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_model_config")
public class ModelConfig {

    @TableId(type = IdType.ASSIGN_UUID)
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
