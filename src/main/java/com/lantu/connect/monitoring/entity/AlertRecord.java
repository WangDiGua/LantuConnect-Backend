package com.lantu.connect.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

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

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> labels;

    private LocalDateTime firedAt;
    private LocalDateTime resolvedAt;
}
