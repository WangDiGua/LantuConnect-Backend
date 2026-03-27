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
 * 访问令牌实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName(value = "t_access_token", autoResultMap = true)
public class AccessToken {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String tokenHash;
    private String maskedToken;
    private String type;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scopes;

    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private String createdBy;
    private LocalDateTime createTime;
}
