package com.lantu.connect.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源级限流实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_quota_rate_limit")
public class QuotaRateLimit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String targetType;
    private Long targetId;
    private String targetName;
    private Integer maxRequestsPerMin;
    private Integer maxRequestsPerHour;
    private Integer maxConcurrent;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
