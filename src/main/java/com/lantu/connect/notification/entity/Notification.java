package com.lantu.connect.notification.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type;
    private String title;
    private String body;
    private String sourceType;
    private String sourceId;
    private Boolean isRead;
    private String category;
    private String severity;
    private String aggregateKey;
    private String flowStatus;
    private Integer currentStep;
    private Integer totalSteps;
    private String stepsJson;
    private String actionLabel;
    private String actionUrl;
    private String metadataJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime lastEventTime;

    @TableField(exist = false)
    private String stepKey;

    @TableField(exist = false)
    private String stepTitle;

    @TableField(exist = false)
    private String stepStatus;

    @TableField(exist = false)
    private String stepSummary;
}
