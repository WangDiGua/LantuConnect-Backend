package com.lantu.connect.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台角色实体
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@TableName(value = "t_platform_role", autoResultMap = true)
public class PlatformRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleCode;
    private String roleName;
    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> permissions;

    private Boolean isSystem;
    private Integer userCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
