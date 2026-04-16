package com.lantu.connect.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "t_alert_record_action", autoResultMap = true)
public class AlertRecordAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String recordId;
    private String actionType;
    private Long operatorUserId;
    private String note;
    private String previousStatus;
    private String nextStatus;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraJson;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private String operatorName;
}
