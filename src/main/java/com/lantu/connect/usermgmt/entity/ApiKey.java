package com.lantu.connect.usermgmt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API密钥实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName(value = "t_api_key", autoResultMap = true)
public class ApiKey {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String keyHash;
    private String prefix;
    private String maskedKey;
    private String ownerType;
    private String ownerId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scopes;

    /** 非空时网关仅允许访问该集成套餐内资源（见 t_integration_package） */
    private String integrationPackageId;

    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private Long callCount;
    private String createdBy;
    @TableField(exist = false)
    private String createdByName;
    private LocalDateTime createTime;
}
