package com.lantu.connect.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_audit_log")
public class AuditLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String username;
    private String action;
    private String resource;
    private String resourceId;
    private String details;
    private String ip;
    private String userAgent;
    private String result;
    private LocalDateTime createTime;
}
