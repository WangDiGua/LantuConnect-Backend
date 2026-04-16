package com.lantu.connect.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 告警记录实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName(value = "t_alert_record", autoResultMap = true)
public class AlertRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String ruleId;
    private String ruleName;
    private String severity;
    private String status;
    private String message;
    private String source;
    private Long assigneeUserId;
    private LocalDateTime ackAt;
    private LocalDateTime silencedAt;
    private LocalDateTime reopenedAt;
    private BigDecimal lastSampleValue;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> labels;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> triggerSnapshotJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> ruleSnapshotJson;

    private LocalDateTime firedAt;
    private LocalDateTime resolvedAt;

    @TableField(exist = false)
    private String assigneeName;

    @TableField(exist = false)
    private String scopeType;

    @TableField(exist = false)
    private String scopeLabel;

    @TableField(exist = false)
    private String resourceType;

    @TableField(exist = false)
    private Long resourceId;

    @TableField(exist = false)
    private String resourceName;

    @TableField(exist = false)
    private String ruleMetric;

    @TableField(exist = false)
    private String ruleOperator;

    @TableField(exist = false)
    private BigDecimal ruleThreshold;

    @TableField(exist = false)
    private String ruleDuration;

    @TableField(exist = false)
    private String ruleExpression;

    @TableField(exist = false)
    private String triggerReason;

    @TableField(exist = false)
    private Long activeSeconds;

    @TableField(exist = false)
    private Integer notificationCount;
}
