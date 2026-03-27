package com.lantu.connect.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核 AuditItem 实体
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@TableName("t_audit_item")
public class AuditItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;

    private Long targetId;

    private String displayName;

    private String agentName;

    private String description;

    private String agentType;

    private String sourceType;

    private String submitter;
    @TableField(exist = false)
    private String submitterName;

    private LocalDateTime submitTime;

    private String status;

    private Long reviewerId;
    @TableField(exist = false)
    private String reviewerName;

    private String rejectReason;

    private LocalDateTime reviewTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
