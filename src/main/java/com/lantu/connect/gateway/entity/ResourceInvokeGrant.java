package com.lantu.connect.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源调用授权（grant）实体。
 */
@Data
@TableName(value = "t_resource_invoke_grant", autoResultMap = true)
public class ResourceInvokeGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String resourceType;
    private Long resourceId;
    private String granteeType;
    private String granteeId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> actions;

    private String status;
    private Long grantedByUserId;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
