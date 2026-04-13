package com.lantu.connect.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康检查配置实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_resource_runtime_policy")
public class HealthConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("resource_code")
    private String agentName;

    @TableField("resource_type")
    private String agentType;

    private String displayName;
    private String checkType;
    private String checkUrl;
    private Integer intervalSec;
    private Integer healthyThreshold;
    private Integer timeoutSec;
    private String healthStatus;
    private LocalDateTime lastCheckTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
