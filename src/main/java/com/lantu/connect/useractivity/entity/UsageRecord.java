package com.lantu.connect.useractivity.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 使用记录实体，底层数据已合并到 t_call_log。
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_call_log")
public class UsageRecord {

    @TableId(value = "usage_id", type = IdType.INPUT)
    private Long id;

    @TableField("user_id")
    private Long userId;
    private String agentName;
    private String displayName;
    @TableField("resource_type")
    private String type;

    /** 对应 {@code t_resource.id}；网关 invoke 写入，历史数据可为 null */
    @TableField("agent_id")
    private Long resourceId;

    private String action;
    private String inputPreview;
    private String outputPreview;
    private Integer latencyMs;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
