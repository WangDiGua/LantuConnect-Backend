package com.lantu.connect.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 限流规则实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_rate_limit_rule")
public class RateLimitRule {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String target;
    private String targetValue;

    /** null / all：任意资源类型；否则仅对对应 resource_type 的调用生效（网关侧） */
    private String resourceScope;
    private Long windowMs;
    private Integer maxRequests;
    private Integer maxTokens;
    private Integer burstLimit;
    private String action;
    private Boolean enabled;
    private Integer priority;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
