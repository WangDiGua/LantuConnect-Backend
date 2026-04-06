package com.lantu.connect.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感操作审计日志（对应 t_sensitive_action_audit）。
 */
@Data
@TableName("t_sensitive_action_audit")
public class SensitiveActionAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String actionType;

    private String targetId;

    private Integer success;

    private String failReason;

    private String clientIp;

    private LocalDateTime createdAt;
}
