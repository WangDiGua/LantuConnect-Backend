package com.lantu.connect.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配额实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_quota")
public class Quota {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;
    private Long targetId;
    private String targetName;

    /** all | agent | skill | mcp | app | dataset */
    private String resourceCategory;
    private Integer dailyLimit;
    private Integer monthlyLimit;
    private Integer dailyUsed;
    private Integer monthlyUsed;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
