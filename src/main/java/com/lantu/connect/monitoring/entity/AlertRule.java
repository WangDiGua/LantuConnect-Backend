package com.lantu.connect.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName(value = "t_alert_rule", autoResultMap = true)
public class AlertRule {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String description;
    private String metric;
    private String operator;
    private BigDecimal threshold;
    private String duration;
    private String severity;
    private Boolean enabled;

    /**
     * 历史列；当前仅站内通知，服务端创建/更新时始终写入空列表，前端不再展示外部渠道。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> notifyChannels;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
