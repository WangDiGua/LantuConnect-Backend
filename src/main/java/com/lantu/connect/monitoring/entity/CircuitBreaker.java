package com.lantu.connect.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 熔断器配置实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_resource_circuit_breaker")
public class CircuitBreaker {

    public static final String STATE_OPEN = "OPEN";
    public static final String STATE_HALF_OPEN = "HALF_OPEN";
    public static final String STATE_CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("resource_code")
    private String agentName;

    @TableField("resource_type")
    private String resourceType;

    private String displayName;
    private String currentState;
    private Integer failureThreshold;
    private Integer openDurationSec;
    private Integer halfOpenMaxCalls;
    @TableField("fallback_resource_code")
    private String fallbackAgentName;
    private String fallbackMessage;
    private LocalDateTime lastOpenedAt;
    private Long successCount;
    private Long failureCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
