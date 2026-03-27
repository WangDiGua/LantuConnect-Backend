package com.lantu.connect.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lantu.connect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 服务提供商实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_provider", autoResultMap = true)
public class Provider extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String providerCode;
    private String providerName;
    private String providerType;
    private String description;
    private String authType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;

    private String baseUrl;
    private String status;
    private Integer agentCount;
    private Integer skillCount;
}
