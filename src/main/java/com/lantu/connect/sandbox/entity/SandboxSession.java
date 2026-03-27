package com.lantu.connect.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "t_sandbox_session", autoResultMap = true)
public class SandboxSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionToken;
    private Long ownerUserId;
    private String apiKeyId;
    private String apiKeyPrefix;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedResourceTypes;

    private Integer maxCalls;
    private Integer usedCalls;
    private Integer maxTimeoutSec;
    private LocalDateTime expiresAt;
    private LocalDateTime lastInvokeAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
