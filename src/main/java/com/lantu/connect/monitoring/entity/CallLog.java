package com.lantu.connect.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调用日志，字段与 {@code sql/schema.sql} 中 t_call_log 一致。
 */
@Data
@TableName("t_call_log")
public class CallLog {

    @TableId(type = IdType.INPUT)
    private String id;

    private String traceId;
    private String agentId;
    private String agentName;

    /** 网关调用目标资源类型（agent/skill/mcp/app/dataset）；历史数据可能为 null */
    @TableField("resource_type")
    private String resourceType;

    /** 与表列 user_id 一致，存字符串以兼容 VARCHAR(36) */
    private String userId;
    @TableField(exist = false)
    private String username;

    private String model;
    private String method;
    private String status;

    @TableField("status_code")
    private Integer statusCode;

    private Integer latencyMs;

    private Integer inputTokens;
    private Integer outputTokens;

    private BigDecimal cost;

    @TableField("error_message")
    private String errorMessage;

    private String ip;

    private LocalDateTime createTime;
}
